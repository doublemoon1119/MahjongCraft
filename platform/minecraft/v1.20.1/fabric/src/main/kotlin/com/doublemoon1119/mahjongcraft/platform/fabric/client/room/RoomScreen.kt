package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomScreenActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.TableLobbyPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollState
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.SettingsFooterLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.UnsavedChangesConfirmationScreen
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.PlayerPortraitRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.PublicPlayerIndicatorTextResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongPlayerInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.text.gameConfigPresentationText
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.aiPlayerDisplayName
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigFieldDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceProviderException
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import kotlinx.serialization.json.Json
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.Box
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/** 空桌、等待房間與進行中對局共用的桌級畫面。 */
class RoomScreen(
    private val stateStore: ClientMahjongStateStore,
    private val configPresentations: GameConfigPresentationRegistry,
    private val configResolver: GameConfigPresentationResolver,
    private val ruleNames: RuleModuleDisplayNameRegistry,
    private val portraitRenderer: PlayerPortraitRenderer,
    private val aiStrategies: MahjongAiStrategyRegistry,
    private val aiStrategyNames: AiStrategyDisplayNameRegistry,
    private val appearanceSources: RoomMemberAppearanceSourceRegistry,
    private val indicatorTextResolver: PublicPlayerIndicatorTextResolver,
    private val json: Json,
    private val networkRegistries: NetworkDtoRegistries,
    openSettings: Boolean = false,
) : Screen(Text.translatable(MinecraftRoomScreenKeys.TITLE)) {
    private var page = if (openSettings) Page.SETTINGS else Page.ROOM
    private var selectedCategoryId: String? = null
    private val fieldScroll = ScrollState()
    private var draftConfig: GameConfig? = null
    private var authoritativeConfigAtDraftStart: GameConfig? = null
    private var draftStale = false
    private var validationFailed = false
    private val invalidFieldIds = mutableSetOf<String>()
    private val playingInfoScroll = ScrollState()

    /** 目前正在拖曳哪一列（grid row）的對局資訊 scrollbar；兩列共用同一個捲動位置，但幾何各自獨立。 */
    private var playingInfoDragRow = 0

    /** 等待室玩家卡片 grid 換列時的垂直捲動狀態，避免超出範圍的列疊在底部操作列上。 */
    private val memberScroll = ScrollState()
    private var applyButton: ButtonWidget? = null
    private var undoButton: ButtonWidget? = null
    private var resetButton: ButtonWidget? = null
    private var doneButton: ButtonWidget? = null
    private var returnToRoomAfterApply = false
    private var rebuildRequested = false
    private var lastRoomSnapshot = stateStore.roomSnapshot
    private var lastLobby = stateStore.tableLobby
    private var wasWaitingRoomMember = stateStore.tableLobby?.phase == TableLobbyPhaseDto.WAITING && stateStore.roomSnapshot?.isInRoom == true
    private val profilePreviews = mutableMapOf<Uuid, OtherClientPlayerEntity>()
    private val warnedActorKeys = mutableSetOf<String>()
    private val warnedAppearanceProviderIds = mutableSetOf<String>()

    override fun init() {
        applyButton = null
        undoButton = null
        resetButton = null
        doneButton = null
        addDrawableChild(tabButton(width / 2 - 102, MinecraftRoomScreenKeys.PAGE_ROOM, Page.ROOM))
        addDrawableChild(
            tabButton(width / 2 + 2, MinecraftRoomScreenKeys.PAGE_SETTINGS, Page.SETTINGS).also {
                it.active = currentConfig() != null && page != Page.SETTINGS
            },
        )
        when (page) {
            Page.ROOM -> initRoomPage()
            Page.SETTINGS -> initSettingsPage()
        }
    }

    private fun tabButton(x: Int, key: String, target: Page): ButtonWidget = RestartableMarqueeButtonWidget.builder(Text.translatable(key)) {
        page = target
        rebuild()
    }.dimensions(x, 24, 100, 20).build().also { it.active = page != target }

    private fun initRoomPage() {
        val lobby = stateStore.tableLobby ?: return
        val room = stateStore.roomSnapshot
        val bottom = height - 30
        when (lobby.phase) {
            TableLobbyPhaseDto.EMPTY -> addCenteredActions(
                listOf(ActionButton(MinecraftRoomScreenKeys.CREATE, RoomScreenActionDto.Create(lobby.tableId), true)),
                bottom,
            )
            TableLobbyPhaseDto.PLAYING -> addCenteredActions(emptyList(), bottom)
            TableLobbyPhaseDto.WAITING -> {
                if (room == null) return
                val actions = mutableListOf<ActionButton>()
                fun button(key: String, action: RoomScreenActionDto, active: Boolean = true) = actions.add(ActionButton(key, action, active))
                if (!room.isInRoom) {
                    button(MinecraftRoomScreenKeys.JOIN, RoomScreenActionDto.Join(lobby.tableId), room.playerIds.size < room.gameConfig.ruleConfig.maxPlayers)
                } else if (room.isHost) {
                    button(MinecraftRoomScreenKeys.ADD_AI, RoomScreenActionDto.AddAi(lobby.tableId), room.playerIds.size < room.gameConfig.ruleConfig.maxPlayers)
                    button(MinecraftRoomScreenKeys.START, RoomScreenActionDto.Start(lobby.tableId), room.canStart)
                    button(MinecraftRoomScreenKeys.DISBAND, RoomScreenActionDto.Disband(lobby.tableId))
                    val grid = memberGridLayout(room.playerIds.size)
                    memberScroll.clamp(grid.rows - memberGridVisibleRows())
                    val visibleRows = memberScroll.index until memberScroll.index + memberGridVisibleRows()
                    val gridWidth = grid.columns * grid.cardWidth
                    room.playerIds.forEachIndexed { index, targetId ->
                        if (targetId == room.hostId) return@forEachIndexed
                        val row = index / grid.columns
                        if (row !in visibleRows) return@forEachIndexed
                        val cardX = width / 2 - gridWidth / 2 + (index % grid.columns) * grid.cardWidth
                        val cardY = MEMBER_CARD_TOP + (row - memberScroll.index) * MEMBER_ROW_HEIGHT
                        if (targetId in room.aiPlayerIds) {
                            val current = room.aiPlayerStrategyKeys[targetId]
                            addDrawableChild(
                                RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftRoomScreenKeys.AI_STRATEGY, aiStrategyText(current))) {
                                    val keys = aiStrategies.getAllStrategyKeys().sorted()
                                    if (keys.isNotEmpty()) {
                                        val next = keys[(keys.indexOf(current).coerceAtLeast(0) + 1) % keys.size]
                                        send(RoomScreenActionDto.ChangeAiStrategy(lobby.tableId, targetId.toString(), next))
                                    }
                                }.dimensions(cardX + 8, cardY + AI_STRATEGY_BUTTON_OFFSET_Y, grid.cardWidth - 20, 18).build().also {
                                    it.tooltip = Tooltip.of(aiStrategyTooltip(current))
                                },
                            )
                        }
                        addDrawableChild(
                            RestartableMarqueeButtonWidget.builder(Text.literal("×")) {
                                send(RoomScreenActionDto.Kick(lobby.tableId, targetId.toString()))
                            }.dimensions(cardX + grid.cardWidth - 23, cardY + KICK_BUTTON_OFFSET_Y, 16, 16).build().also {
                                it.tooltip = Tooltip.of(Text.translatable(MinecraftRoomScreenKeys.KICK))
                            },
                        )
                    }
                } else {
                    val selfId = client?.player?.uuid?.let(UUID::toString)
                    val ready = room.readyPlayerIds.any { it.toString() == selfId }
                    button(
                        if (ready) MinecraftRoomScreenKeys.CANCEL_READY else MinecraftRoomScreenKeys.READY,
                        RoomScreenActionDto.ToggleReady(lobby.tableId),
                    )
                    button(MinecraftRoomScreenKeys.LEAVE, RoomScreenActionDto.Leave(lobby.tableId))
                }
                addCenteredActions(actions, bottom)
            }
        }
    }

    private fun initSettingsPage() {
        val authoritative = currentConfig() ?: return
        val room = stateStore.roomSnapshot
        if (draftConfig == null || authoritativeConfigAtDraftStart == null) {
            draftConfig = authoritative
            authoritativeConfigAtDraftStart = authoritative
        }
        val config = draftConfig ?: authoritative
        val resolved = configResolver.resolve(config)
        val moduleId = resolved.ruleModuleId
        val definition = resolved.definition ?: return
        val canEditRoom = stateStore.tableLobby?.phase == TableLobbyPhaseDto.WAITING && room?.isHost == true
        val editable = canEditRoom && definition.selectable
        val categories = definition.categories
        if (selectedCategoryId !in categories.map { it.id }) selectedCategoryId = categories.firstOrNull()?.id
        val compact = isCompactSettings()
        addRuleSelector(config, moduleId, canEditRoom, compact)
        if (compact) {
            val categoryWidth = (width - COMPACT_CONTENT_MARGIN * 2 - COMPACT_FIELDS_GAP) / 2
            categories.forEachIndexed { index, category ->
                addDrawableChild(
                    RestartableMarqueeButtonWidget.builder(Text.translatable(category.nameTranslationKey)) {
                        selectedCategoryId = category.id
                        fieldScroll.reset()
                        rebuild()
                    }.dimensions(
                        COMPACT_CONTENT_MARGIN + index % 2 * (categoryWidth + COMPACT_FIELDS_GAP),
                        COMPACT_CATEGORY_TOP + index / 2 * COMPACT_CATEGORY_ROW_HEIGHT,
                        categoryWidth,
                        20,
                    ).build().also { it.active = selectedCategoryId != category.id },
                )
            }
        } else {
            var categoryY = 82
            categories.forEach { category ->
                addDrawableChild(
                    RestartableMarqueeButtonWidget.builder(Text.translatable(category.nameTranslationKey)) {
                        selectedCategoryId = category.id
                        fieldScroll.reset()
                        rebuild()
                    }.dimensions(18, categoryY, 112, 20).build().also { it.active = selectedCategoryId != category.id },
                )
                categoryY += 24
            }
        }
        val categoryFields = definition.fields.filter { it.categoryId == selectedCategoryId }
        val maximumVisibleFields = maximumVisibleFields()
        fieldScroll.clamp(categoryFields.size - maximumVisibleFields)
        val fieldsTop = settingsFieldsTop()
        val rowHeight = fieldRowHeight()
        categoryFields.drop(fieldScroll.index).take(maximumVisibleFields).forEachIndexed { index, field ->
            val controlY = fieldControlY(fieldsTop + index * rowHeight)
            addFieldControls(field, config, controlY, editable && field.isEditable && field.isEnabled(config))
        }
        if (editable) {
            val defaultConfig = GameConfig(definition.defaultRuleConfig()).withConsistentSpectatorVisibility()
            val footer = settingsFooterLayout()
            val reset = RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftRoomScreenKeys.RESET_DEFAULTS_BUTTON)) {
                draftConfig = defaultConfig
                invalidFieldIds.clear()
                validationFailed = false
                rebuild()
            }.dimensions(footer.resetX, height - 30, footer.resetWidth, 20).build().also {
                it.tooltip = Tooltip.of(resetTooltip())
            }
            resetButton = reset
            addDrawableChild(reset)
            val undo = RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftRoomScreenKeys.UNDO)) {
                restoreAuthoritativeDraft(authoritative)
            }.dimensions(footer.undoX, height - 30, footer.actionWidth, 20).build()
            undoButton = undo
            addDrawableChild(undo)
            val apply = RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftRoomScreenKeys.APPLY)) { applyDraft() }
                .dimensions(footer.applyX, height - 30, footer.actionWidth, 20).build()
            applyButton = apply
            addDrawableChild(apply)
            val done = RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftRoomScreenKeys.DONE)) {
                finishSettings()
            }.dimensions(footer.doneX, height - 30, footer.actionWidth, 20).build().also {
                it.active = !draftStale && !validationFailed
            }
            doneButton = done
            addDrawableChild(done)
            refreshDraftButtons(config, authoritative, defaultConfig)
        } else {
            addDrawableChild(
                RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftRoomScreenKeys.DONE)) {
                    page = Page.ROOM
                    rebuild()
                }.dimensions(width - 94, height - 30, 74, 20).build(),
            )
        }
    }

    private fun addFieldControls(field: GameConfigFieldDefinition, config: GameConfig, y: Int, editable: Boolean) {
        val value = field.read(config)
        val controlY = y
        val compact = isCompactSettings()
        when (val editor = field.editor) {
            GameConfigEditorSpec.BooleanToggle -> {
                val enabled = (value as GameConfigPresentationValue.BooleanValue).enabled
                val (left, controlWidth) = if (compact) compactControlBounds() else (width - 152) to 124
                addDrawableChild(
                    RestartableMarqueeButtonWidget.builder(booleanText(enabled)) {
                        updateDraft(field, GameConfigPresentationValue.BooleanValue(!enabled))
                    }.dimensions(left, controlY, controlWidth, 20).build().withFieldTooltip(field, config, editable),
                )
            }
            is GameConfigEditorSpec.SingleChoice -> {
                val option = (value as GameConfigPresentationValue.ChoiceValue).optionId
                val (left, controlWidth) = if (compact) compactControlBounds() else (width - 212) to 184
                addDrawableChild(
                    RestartableMarqueeButtonWidget.builder(optionText(option)) {
                        val index = editor.optionIds.indexOf(option).coerceAtLeast(0)
                        updateDraft(field, GameConfigPresentationValue.ChoiceValue(editor.optionIds[(index + 1) % editor.optionIds.size]))
                    }.dimensions(left, controlY, controlWidth, 20).build().withFieldTooltip(field, config, editable),
                )
            }
            is GameConfigEditorSpec.IntegerInput -> {
                val number = (value as GameConfigPresentationValue.IntegerValue).number
                val bounds = if (compact) {
                    val (left, controlWidth) = compactControlBounds()
                    val plusX = left + controlWidth - COMPACT_INTEGER_BUTTON_WIDTH
                    val textX = left + COMPACT_INTEGER_BUTTON_WIDTH + COMPACT_INTEGER_BUTTON_GAP
                    val textWidth = (plusX - COMPACT_INTEGER_BUTTON_GAP - textX).coerceAtLeast(1)
                    IntegerControlBounds(left, textX, textWidth, plusX)
                } else {
                    IntegerControlBounds(width - 212, width - 184, 128, width - 52)
                }
                addDrawableChild(
                    RestartableMarqueeButtonWidget.builder(Text.literal("−")) {
                        val next = RoomConfigIntegerControl.decrement(
                            current = number,
                            editor = editor,
                            shiftHeld = hasShiftDown(),
                        )
                        updateDraft(field, GameConfigPresentationValue.IntegerValue(next))
                    }.dimensions(bounds.minusX, controlY, COMPACT_INTEGER_BUTTON_WIDTH, 20).build().withFieldTooltip(
                        field,
                        config,
                        editable && RoomConfigIntegerControl.canDecrease(current = number, editor = editor),
                    ),
                )
                val input = CenteredIntegerTextFieldWidget(
                    textRenderer,
                    bounds.textX,
                    controlY,
                    bounds.textWidth,
                    20,
                    Text.translatable(field.nameTranslationKey),
                ).also { widget ->
                    widget.text = number?.toString().orEmpty()
                    widget.setMaxLength(11)
                    widget.setEditable(editable)
                    widget.active = editable
                    widget.setEditableColor(0xFFFFFF)
                    widget.setUneditableColor(0xAAAAAA)
                    widget.tooltip = Tooltip.of(fieldTooltip(field, config))
                    widget.setChangedListener { raw ->
                        updateNumericDraft(field, editor, raw)
                        widget.setEditableColor(if (field.id in invalidFieldIds) 0xFF5555 else 0xFFFFFF)
                    }
                }
                addDrawableChild(input)
                addDrawableChild(
                    RestartableMarqueeButtonWidget.builder(Text.literal("+")) {
                        val next = RoomConfigIntegerControl.increment(
                            current = number,
                            editor = editor,
                            shiftHeld = hasShiftDown(),
                        )
                        updateDraft(field, GameConfigPresentationValue.IntegerValue(next))
                    }.dimensions(bounds.plusX, controlY, COMPACT_INTEGER_BUTTON_WIDTH, 20).build().withFieldTooltip(
                        field,
                        config,
                        editable && RoomConfigIntegerControl.canIncrease(current = number, editor = editor),
                    ),
                )
            }
        }
    }

    /** 窄視窗時控制項改用單欄、依可用寬度伸縮的版面，取代固定從右邊界回推的座標。 */
    private fun compactControlBounds(): Pair<Int, Int> {
        val left = COMPACT_CONTENT_MARGIN
        val controlWidth = (width - COMPACT_CONTENT_MARGIN - COMPACT_SCROLLBAR_RESERVE).coerceAtLeast(MIN_COMPACT_CONTROL_WIDTH)
        return left to controlWidth
    }

    private data class IntegerControlBounds(val minusX: Int, val textX: Int, val textWidth: Int, val plusX: Int)

    private fun ButtonWidget.withFieldTooltip(field: GameConfigFieldDefinition, config: GameConfig, active: Boolean): ButtonWidget = apply {
        this.active = active
        tooltip = Tooltip.of(fieldTooltip(field, config))
    }

    private fun updateDraft(field: GameConfigFieldDefinition, value: GameConfigPresentationValue) {
        val updater = field.update ?: return
        val updated = runCatching { updater(draftConfig ?: return, value) }.getOrNull()
        if (updated == null) invalidFieldIds.add(field.id) else invalidFieldIds.remove(field.id)
        validationFailed = invalidFieldIds.isNotEmpty()
        if (updated != null) draftConfig = updated.withConsistentSpectatorVisibility()
        rebuild()
    }

    /** 直接輸入整數時保留焦點，只更新草稿與驗證狀態。 */
    private fun updateNumericDraft(
        field: GameConfigFieldDefinition,
        editor: GameConfigEditorSpec.IntegerInput,
        raw: String,
    ) {
        val number = raw.toIntOrNull()
        val valid = (editor.nullable && raw.isEmpty()) || number != null && number in editor.minimum..editor.maximum
        if (valid) invalidFieldIds.remove(field.id) else invalidFieldIds.add(field.id)
        validationFailed = invalidFieldIds.isNotEmpty()
        refreshDraftButtons()
        if (!valid) return
        val updater = field.update ?: return
        val updated = runCatching {
            updater(draftConfig ?: return, GameConfigPresentationValue.IntegerValue(number))
        }.getOrNull()
        if (updated == null) invalidFieldIds.add(field.id) else invalidFieldIds.remove(field.id)
        validationFailed = invalidFieldIds.isNotEmpty()
        refreshDraftButtons()
        if (updated != null) draftConfig = updated.withConsistentSpectatorVisibility()
    }

    /** 關閉旁觀時同時關閉旁觀者手牌公開，避免草稿存在互相矛盾的設定。 */
    private fun GameConfig.withConsistentSpectatorVisibility(): GameConfig = if (
        flowConfig.spectatingPolicy == SpectatingPolicy.DISABLED
    ) {
        copy(
            flowConfig = flowConfig.copy(
                spectatorHandVisibility = SpectatorHandVisibility.HIDDEN,
            ),
        )
    } else {
        this
    }

    /** 以單一切換按鈕顯示規則，並在 tooltip 條列所有已登記規則；窄視窗時改為佔滿內容寬度的單行。 */
    private fun addRuleSelector(config: GameConfig, moduleId: String, canEdit: Boolean, compact: Boolean) {
        val candidates = configPresentations.ruleModuleIds.sorted()
        val currentName = ruleName(moduleId)
        val (x, buttonWidth) = if (compact) COMPACT_CONTENT_MARGIN to (width - COMPACT_CONTENT_MARGIN * 2) else 18 to 112
        addDrawableChild(
            RestartableMarqueeButtonWidget.builder(currentName) {
                val selectable = candidates.filter { configPresentations.find(it)?.selectable == true }
                if (selectable.isEmpty()) return@builder
                val nextId = selectable[(selectable.indexOf(moduleId).coerceAtLeast(0) + 1) % selectable.size]
                val next = configPresentations.find(nextId) ?: return@builder
                draftConfig = config.copy(ruleConfig = next.defaultRuleConfig())
                selectedCategoryId = null
                fieldScroll.reset()
                invalidFieldIds.clear()
                validationFailed = false
                rebuild()
            }.dimensions(x, 54, buttonWidth, 20).build().also { button ->
                button.active = canEdit && candidates.count { configPresentations.find(it)?.selectable == true } > 1
                button.tooltip = Tooltip.of(
                    Text.empty()
                        .append(Text.translatable(MinecraftRoomScreenKeys.CURRENT_VALUE, currentName).formatted(Formatting.GREEN))
                        .append("\n")
                        .append(Text.translatable(MinecraftRoomScreenKeys.AVAILABLE_OPTIONS).formatted(Formatting.GOLD))
                        .also { tooltip ->
                            candidates.forEach { candidateId ->
                                val candidate = configPresentations.find(candidateId) ?: return@forEach
                                tooltip.append("\n• ").append(ruleName(candidateId).copy().formatted(if (candidate.selectable) Formatting.WHITE else Formatting.RED))
                                candidate.unavailableReasonTranslationKey?.let { tooltip.append(" — ").append(Text.translatable(it).formatted(Formatting.RED)) }
                            }
                        },
                )
            },
        )
    }

    /** 建立包含說明、目前值、選項或數值限制的通用條列 tooltip。 */
    private fun fieldTooltip(field: GameConfigFieldDefinition, config: GameConfig): Text {
        val result = Text.empty().append(Text.translatable(field.descriptionTranslationKey).formatted(Formatting.GRAY))
        val current = field.read(config)
        result.append("\n• ").append(
            Text.translatable(MinecraftRoomScreenKeys.CURRENT_VALUE, presentationText(current)).formatted(Formatting.GREEN),
        )
        when (val editor = field.editor) {
            GameConfigEditorSpec.BooleanToggle -> {
                result.append("\n• ").append(Text.translatable(MinecraftRoomScreenKeys.AVAILABLE_OPTIONS).formatted(Formatting.GOLD))
                result.append("\n  • ").append(booleanText(true))
                result.append("\n  • ").append(booleanText(false))
            }
            is GameConfigEditorSpec.SingleChoice -> {
                result.append("\n• ").append(Text.translatable(MinecraftRoomScreenKeys.AVAILABLE_OPTIONS).formatted(Formatting.GOLD))
                editor.optionIds.forEach { option ->
                    result.append("\n  • ").append(optionText(option).copy().formatted(if ((current as GameConfigPresentationValue.ChoiceValue).optionId == option) Formatting.GREEN else Formatting.WHITE))
                }
            }
            is GameConfigEditorSpec.IntegerInput -> {
                result.append("\n• ").append(
                    Text.translatable(
                        MinecraftRoomScreenKeys.VALID_RANGE,
                        formatInteger(editor.minimum),
                        formatInteger(editor.maximum),
                    ).formatted(Formatting.GOLD),
                )
                result.append("\n• ").append(
                    Text.translatable(MinecraftRoomScreenKeys.NORMAL_STEP, formatInteger(editor.step)).formatted(Formatting.WHITE),
                )
                result.append("\n• ").append(
                    Text.translatable(MinecraftRoomScreenKeys.SHIFT_STEP, formatInteger(editor.step * 10)).formatted(Formatting.YELLOW),
                )
                result.append("\n• ").append(Text.translatable(MinecraftRoomScreenKeys.KEYBOARD_INPUT).formatted(Formatting.GRAY))
            }
        }
        if (!field.isEnabled(config)) {
            result.append("\n• ").append(Text.translatable(MinecraftRoomScreenKeys.DISABLED_BY_DEPENDENCY).formatted(Formatting.RED))
        }
        return result
    }

    private fun presentationText(value: GameConfigPresentationValue): Text = gameConfigPresentationText(value)

    private fun ruleName(moduleId: String): Text = ruleNames.find(moduleId)?.let(Text::translatable) ?: Text.literal(moduleId)

    /** 依目前實際按鈕數量將底部操作列整組置中。 */
    private fun addCenteredActions(actions: List<ActionButton>, y: Int) {
        val buttonCount = actions.size + 1
        val gap = 6
        val buttonWidth = minOf(88, (width - 16 - (buttonCount - 1) * gap) / buttonCount)
        val totalWidth = buttonCount * buttonWidth + (buttonCount - 1) * gap
        var x = width / 2 - totalWidth / 2
        actions.forEach { action ->
            addActionButton(x, y, buttonWidth, action.key, action.action, action.active)
            x += buttonWidth + gap
        }
        addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftRoomScreenKeys.CLOSE)) {
                closeEntireScreen()
            }.dimensions(x, y, buttonWidth, 20).build(),
        )
    }

    /** 依實際畫面寬度配置固定單行的設定頁 footer。 */
    private fun settingsFooterLayout(): SettingsFooterLayout {
        val availableWidth = width - 20
        return SettingsFooterLayout.create(
            left = 10,
            availableWidth = availableWidth,
            preferredResetWidth = 104,
            gap = 6,
        )
    }

    private fun aiStrategyTooltip(current: String?): Text = Text.empty()
        .append(Text.translatable(MinecraftRoomScreenKeys.AI_STRATEGY_TITLE).formatted(Formatting.GOLD))
        .append("\n• ")
        .append(Text.translatable(MinecraftRoomScreenKeys.CURRENT_VALUE, aiStrategyText(current)).formatted(Formatting.GREEN))
        .append("\n")
        .append(Text.translatable(MinecraftRoomScreenKeys.AVAILABLE_OPTIONS).formatted(Formatting.GOLD))
        .also { tooltip ->
            aiStrategies.getAllStrategyKeys().sorted().forEach { key ->
                tooltip.append("\n• ").append(aiStrategyText(key).copy().formatted(if (key == current) Formatting.GREEN else Formatting.WHITE))
            }
        }

    private fun applyDraft() {
        if (draftStale || validationFailed) return
        val lobby = stateStore.tableLobby ?: return
        val config = draftConfig ?: return
        MahjongChannels.roomScreenAction.sendToServer(json, RoomScreenActionDto.UpdateConfig(lobby.tableId, config.toDto(networkRegistries)))
    }

    /** 有變更時提交並等待權威 snapshot，沒有變更時立即返回玩家頁。 */
    private fun finishSettings() {
        val authoritative = currentConfig() ?: return
        val draft = draftConfig ?: authoritative
        if (draft == authoritative) {
            page = Page.ROOM
            rebuild()
            return
        }
        returnToRoomAfterApply = true
        applyDraft()
    }

    /** 將草稿恢復成目前權威設定。 */
    private fun restoreAuthoritativeDraft(authoritative: GameConfig? = currentConfig()) {
        val resolvedAuthoritative = authoritative ?: return
        draftConfig = resolvedAuthoritative
        authoritativeConfigAtDraftStart = resolvedAuthoritative
        invalidFieldIds.clear()
        validationFailed = false
        draftStale = false
        returnToRoomAfterApply = false
        rebuild()
    }

    /** 依草稿、權威值與預設值同步儲存、取消及重設按鈕狀態。 */
    private fun refreshDraftButtons(
        draft: GameConfig? = draftConfig,
        authoritative: GameConfig? = currentConfig(),
        defaults: GameConfig? = draft?.let { config ->
            configResolver.resolve(config).definition?.let { GameConfig(it.defaultRuleConfig()) }
        },
    ) {
        val hasUnsavedChanges = draft != null && authoritative != null && draft != authoritative
        applyButton?.active = hasUnsavedChanges && !draftStale && !validationFailed
        undoButton?.active = hasUnsavedChanges || draftStale || validationFailed
        resetButton?.active = draft != null && defaults != null && draft != defaults
        doneButton?.active = !draftStale && !validationFailed
        val changes = if (draft != null && authoritative != null && draft != authoritative && !draftStale) {
            Tooltip.of(gameConfigDifferenceText(configResolver, ruleNames, authoritative, draft))
        } else {
            null
        }
        applyButton?.tooltip = changes
        undoButton?.tooltip = changes ?: Tooltip.of(undoTooltip())
    }

    private fun undoTooltip(): Text = Text.empty()
        .append(Text.translatable(MinecraftRoomScreenKeys.UNDO).formatted(Formatting.GOLD))
        .append("\n• ").append(Text.translatable(MinecraftRoomScreenKeys.UNDO_DISCARD).formatted(Formatting.WHITE))
        .append("\n• ").append(Text.translatable(MinecraftRoomScreenKeys.UNDO_RESTORE).formatted(Formatting.GRAY))

    private fun resetTooltip(): Text = Text.empty()
        .append(Text.translatable(MinecraftRoomScreenKeys.RESET_DEFAULTS_BUTTON).formatted(Formatting.GOLD))
        .append("\n• ").append(Text.translatable(MinecraftRoomScreenKeys.RESET_DEFAULTS).formatted(Formatting.WHITE))
        .append("\n• ").append(Text.translatable(MinecraftRoomScreenKeys.RESET_NOT_SAVED).formatted(Formatting.GRAY))

    private fun addActionButton(x: Int, y: Int, width: Int, key: String, action: RoomScreenActionDto, active: Boolean = true) {
        addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(key)) {
                send(action)
            }.dimensions(x, y, width, 20).build().also { it.active = active },
        )
    }

    private fun send(action: RoomScreenActionDto) = MahjongChannels.roomScreenAction.sendToServer(json, action)

    private fun aiStrategyText(strategyKey: String?): Text = strategyKey?.let { key ->
        aiStrategyNames.find(key)?.let(Text::translatable) ?: Text.literal(key)
    } ?: Text.literal("AI")

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFD700)
        val labelTooltip = when (page) {
            Page.ROOM -> {
                renderRoom(context, mouseX, mouseY)
                null
            }
            Page.SETTINGS -> renderSettings(context, mouseX, mouseY)
        }
        super.render(context, mouseX, mouseY, delta)
        labelTooltip?.let { context.drawTooltip(textRenderer, it, mouseX, mouseY) }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (amount == 0.0) return super.mouseScrolled(mouseX, mouseY, amount)
        if (page == Page.SETTINGS) {
            if (fieldScroll.scrollBy(amount, maximumFieldScroll())) rebuild()
            return true
        }
        if (page == Page.ROOM && stateStore.tableLobby?.phase == TableLobbyPhaseDto.PLAYING) {
            playingInfoScroll.scrollBy(amount, maximumPlayingInfoScroll())
            return true
        }
        val waitingGrid = memberGridForWaitingRoom()
        if (page == Page.ROOM && waitingGrid != null) {
            if (memberScroll.scrollBy(amount, (waitingGrid.rows - memberGridVisibleRows()).coerceAtLeast(0))) rebuild()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (page == Page.SETTINGS && button == 0 && isOverScrollbar(mouseX, mouseY)) {
            if (fieldScroll.beginDrag(mouseY, fieldScrollbarLayout())) rebuild()
            return true
        }
        if (page == Page.ROOM && button == 0) {
            val row = playingInfoScrollbarRowAt(mouseX, mouseY)
            if (row != null) {
                playingInfoDragRow = row
                playingInfoScroll.beginDrag(mouseY, playingInfoScrollbarLayout(row))
                return true
            }
            val waitingGrid = memberGridForWaitingRoom()
            if (waitingGrid != null && isOverMemberGridScrollbar(mouseX, mouseY, waitingGrid.rows)) {
                if (memberScroll.beginDrag(mouseY, memberGridScrollbarLayout(waitingGrid.rows))) rebuild()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (fieldScroll.dragging && button == 0) {
            if (fieldScroll.dragTo(mouseY, fieldScrollbarLayout())) rebuild()
            return true
        }
        if (playingInfoScroll.dragging && button == 0) {
            playingInfoScroll.dragTo(mouseY, playingInfoScrollbarLayout(playingInfoDragRow))
            return true
        }
        if (memberScroll.dragging && button == 0) {
            val rows = memberGridForWaitingRoom()?.rows ?: 0
            if (memberScroll.dragTo(mouseY, memberGridScrollbarLayout(rows))) rebuild()
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        fieldScroll.endDrag()
        playingInfoScroll.endDrag()
        memberScroll.endDrag()
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun tick() {
        super.tick()
        if (rebuildRequested) {
            rebuildRequested = false
            clearAndInit()
            return
        }
        if (!isTableStillReachable()) {
            closeEntireScreen()
            return
        }
        if (stateStore.tableLobby != lastLobby || stateStore.roomSnapshot != lastRoomSnapshot) {
            val previousLobby = lastLobby
            val previousRoom = lastRoomSnapshot
            val currentLobby = stateStore.tableLobby
            if (previousLobby?.phase == TableLobbyPhaseDto.WAITING && previousRoom?.isInRoom == true) {
                wasWaitingRoomMember = true
            }
            if (
                previousLobby?.phase == TableLobbyPhaseDto.WAITING &&
                currentLobby?.phase == TableLobbyPhaseDto.PLAYING &&
                wasWaitingRoomMember
            ) {
                closeEntireScreen()
                return
            }
            lastLobby = stateStore.tableLobby
            lastRoomSnapshot = stateStore.roomSnapshot
            if (returnToRoomAfterApply && currentConfig() == draftConfig) {
                val authoritative = currentConfig()
                draftConfig = authoritative
                authoritativeConfigAtDraftStart = authoritative
                returnToRoomAfterApply = false
                page = Page.ROOM
            }
            if (stateStore.tableLobby?.phase == TableLobbyPhaseDto.EMPTY) {
                wasWaitingRoomMember = false
                page = Page.ROOM
                draftConfig = null
                authoritativeConfigAtDraftStart = null
                invalidFieldIds.clear()
                validationFailed = false
            }
            clearAndInit()
            return
        }
        val authoritative = currentConfig() ?: return
        val previous = authoritativeConfigAtDraftStart ?: return
        if (authoritative == previous) return
        if (draftConfig == previous || authoritative == draftConfig) {
            draftConfig = authoritative
            authoritativeConfigAtDraftStart = authoritative
            draftStale = false
            if (returnToRoomAfterApply) {
                returnToRoomAfterApply = false
                page = Page.ROOM
            }
            clearAndInit()
        } else {
            draftStale = true
            returnToRoomAfterApply = false
            refreshDraftButtons()
        }
    }

    /** 離開桌旁或切換維度時關閉畫面，讓 removed() 清除暫時 observer。 */
    private fun isTableStillReachable(): Boolean {
        val lobby = stateStore.tableLobby ?: return false
        val x = lobby.tableX ?: return true
        val y = lobby.tableY ?: return true
        val z = lobby.tableZ ?: return true
        val expectedDimension = lobby.dimensionId ?: return true
        val player = client?.player ?: return false
        val actualDimension = client?.world?.registryKey?.value?.toString() ?: return false
        return actualDimension == expectedDimension && player.squaredDistanceTo(x + 0.5, y + 0.5, z + 0.5) <= 64.0
    }

    private fun renderRoom(context: DrawContext, mouseX: Int, mouseY: Int) {
        val lobby = stateStore.tableLobby ?: return
        when (lobby.phase) {
            TableLobbyPhaseDto.EMPTY -> context.drawCenteredTextWithShadow(textRenderer, Text.translatable(MinecraftRoomScreenKeys.EMPTY), width / 2, 70, 0xFFFFFF)
            TableLobbyPhaseDto.PLAYING -> {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable(MinecraftRoomScreenKeys.PLAYING), width / 2, 50, 0xFFCC55)
                renderPlayingMembers(context, resolvePlayingPlayerInfo(), mouseX, mouseY)
            }
            TableLobbyPhaseDto.WAITING -> stateStore.roomSnapshot?.let { room ->
                renderMembers(
                    context,
                    room.playerIds,
                    room.aiPlayerIds.toSet(),
                    MemberStatus.Waiting(room.readyPlayerIds.toSet(), room.hostId),
                    mouseX,
                    mouseY,
                )
            }
        }
    }

    /** 進行中對局依權威自風排序，固定呈現東、南、西、北。 */
    private fun renderPlayingMembers(
        context: DrawContext,
        players: List<MahjongPlayerInfoEntry>,
        mouseX: Int,
        mouseY: Int,
    ) {
        val sortedPlayers = players.sortedBy { WIND_ORDER.getValue(it.seatWind) }
        val dealerPlayerId = resolvePlayerInfoEntity()?.dealerPlayerId ?: stateStore.gameSnapshot?.dealerPlayerId
        val visibleRows = visiblePlayingInfoRows()
        val maximumScroll = maximumPlayingInfoScroll()
        playingInfoScroll.clamp(maximumScroll)
        val grid = playingGrid()
        val gridWidth = grid.columns * grid.cardWidth
        sortedPlayers.forEachIndexed { index, player ->
            val gridRow = index / grid.columns
            val x = width / 2 - gridWidth / 2 + (index % grid.columns) * grid.cardWidth
            val y = playingRowTop(gridRow)
            context.fill(x + 2, y, x + grid.cardWidth - 4, playingCardBottom(gridRow), MEMBER_CARD_BACKGROUND)
            renderMemberAppearance(context, player.playerId, player.isAi, x, y, grid.cardWidth, mouseX, mouseY)
            context.drawCenteredTextWithShadow(
                textRenderer,
                fitText(Text.literal(player.playerName), grid.cardWidth - 12),
                x + grid.cardWidth / 2,
                y + MEMBER_NAME_OFFSET,
                0xFFFFFF,
            )
            val infoTop = playingInfoTop(gridRow)
            playingInfoRows(player, dealerPlayerId).drop(playingInfoScroll.index).take(visibleRows).forEachIndexed { infoRowIndex, row ->
                context.drawCenteredTextWithShadow(
                    textRenderer,
                    fitText(row.first, grid.cardWidth - 12),
                    x + grid.cardWidth / 2,
                    infoTop + infoRowIndex * PLAYING_INFO_ROW_HEIGHT,
                    row.second,
                )
            }
        }
        renderPlayingInfoScrollbar(context, maximumScroll)
    }

    private fun playingInfoRows(player: MahjongPlayerInfoEntry, dealerPlayerId: Uuid?): List<Pair<Text, Int>> = buildList {
        val wind = windText(player.seatWind)
        add(
            if (player.playerId == dealerPlayerId) {
                Text.empty().append(wind).append("  ●") to 0xFFFFD45A.toInt()
            } else {
                wind to 0xFFFFD45A.toInt()
            },
        )
        add(Text.literal(formatInteger(player.score)) to 0xFFF3F3F3.toInt())
        addAll(player.indicators.map(indicatorTextResolver::resolve))
    }

    /** 優先使用 Player Info entity 的完整公開快照；同步尚未抵達時以遊戲快照安全降級。 */
    private fun resolvePlayingPlayerInfo(): List<MahjongPlayerInfoEntry> {
        resolvePlayerInfoEntity()?.players?.takeIf { it.isNotEmpty() }?.let { return it }
        val snapshot = stateStore.gameSnapshot ?: return emptyList()
        return snapshot.players.mapIndexed { index, player ->
            MahjongPlayerInfoEntry(
                playerId = player.id,
                playerName = memberName(player.id, player.isAi, snapshot.players.filter { it.isAi }.map { it.id }).string,
                isAi = player.isAi,
                seatIndex = index,
                seatWind = player.seatWind,
                score = player.score,
                indicators = emptyList(),
            )
        }
    }

    private fun resolvePlayerInfoEntity(): MahjongPlayerInfoEntity? {
        val lobby = stateStore.tableLobby ?: return null
        val tableId = runCatching { Uuid.parse(lobby.tableId) }.getOrNull() ?: return null
        val world = client?.world ?: return null
        val x = lobby.tableX ?: return null
        val y = lobby.tableY ?: return null
        val z = lobby.tableZ ?: return null
        return world.getEntitiesByClass(
            MahjongPlayerInfoEntity::class.java,
            Box(x - 4.0, y - 4.0, z - 4.0, x + 5.0, y + 5.0, z + 5.0),
        ) { it.managedTableId == tableId }.firstOrNull()
    }

    private fun renderMembers(
        context: DrawContext,
        playerIds: List<Uuid>,
        aiIds: Set<Uuid>,
        memberStatus: MemberStatus,
        mouseX: Int,
        mouseY: Int,
    ) {
        val grid = memberGridLayout(playerIds.size)
        memberScroll.clamp(grid.rows - memberGridVisibleRows())
        val visibleRows = memberScroll.index until memberScroll.index + memberGridVisibleRows()
        val gridWidth = grid.columns * grid.cardWidth
        playerIds.forEachIndexed { index, playerId ->
            val row = index / grid.columns
            if (row !in visibleRows) return@forEachIndexed
            val x = width / 2 - gridWidth / 2 + (index % grid.columns) * grid.cardWidth
            val y = MEMBER_CARD_TOP + (row - memberScroll.index) * MEMBER_ROW_HEIGHT
            context.fill(x + 2, y, x + grid.cardWidth - 4, y + 146, 0xA0202838.toInt())
            val ai = playerId in aiIds
            renderMemberAppearance(context, playerId, ai, x, y, grid.cardWidth, mouseX, mouseY)
            context.drawCenteredTextWithShadow(
                textRenderer,
                fitText(memberName(playerId, ai, playerIds.filter(aiIds::contains)), grid.cardWidth - 12),
                x + grid.cardWidth / 2,
                y + MEMBER_NAME_OFFSET,
                0xFFFFFF,
            )
            val status = when (memberStatus) {
                MemberStatus.Playing -> null
                is MemberStatus.Waiting -> when {
                    playerId == memberStatus.hostId -> Text.translatable(MinecraftRoomScreenKeys.HOST)
                    ai -> Text.translatable(MinecraftRoomScreenKeys.AI)
                    playerId in memberStatus.readyPlayerIds -> Text.translatable(MinecraftRoomScreenKeys.MEMBER_READY)
                    else -> Text.translatable(MinecraftRoomScreenKeys.MEMBER_NOT_READY)
                }
            }
            status?.let {
                val highlighted = memberStatus is MemberStatus.Waiting && (playerId in memberStatus.readyPlayerIds || ai)
                context.drawCenteredTextWithShadow(textRenderer, it, x + grid.cardWidth / 2, y + 111, if (highlighted) 0x88FF88 else 0xAAAAAA)
            }
        }
        if (grid.rows > memberGridVisibleRows()) {
            val layout = memberGridScrollbarLayout(grid.rows)
            context.fill(width - 14, layout.trackTop, width - 9, layout.trackBottom, 0x80505050.toInt())
            context.fill(width - 14, layout.thumbTop, width - 9, layout.thumbTop + layout.thumbHeight, 0xFFD0D0D0.toInt())
        }
    }

    private fun renderMemberAppearance(
        context: DrawContext,
        playerId: Uuid,
        ai: Boolean,
        x: Int,
        y: Int,
        cardWidth: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val appearance = runCatching { appearanceSources.resolve(RoomMemberAppearanceContext(playerId, ai)) }
            .onFailure { cause ->
                val providerId = (cause as? RoomMemberAppearanceSourceProviderException)?.providerId ?: "registry"
                if (warnedAppearanceProviderIds.add(providerId)) {
                    LOGGER.warn("Failed to resolve room member appearance provider {}", providerId, cause)
                }
            }
            .getOrDefault(if (ai) RoomMemberAppearanceSource.Portrait else RoomMemberAppearanceSource.PlayerModel)
        when (appearance) {
            RoomMemberAppearanceSource.PlayerModel -> {
                val entity = resolvePlayerPreview(playerId)
                if (entity != null) {
                    InventoryScreen.drawEntity(
                        context,
                        x + cardWidth / 2,
                        y + 84,
                        (cardWidth / 3).coerceIn(24, 36),
                        x + cardWidth / 2 - mouseX.toFloat(),
                        y + 42 - mouseY.toFloat(),
                        entity,
                    )
                }
            }
            RoomMemberAppearanceSource.Portrait -> renderPortrait(context, playerId, ai, x, y, cardWidth)
            is RoomMemberAppearanceSource.ActorPreview -> {
                if (warnedActorKeys.add(appearance.actorKey)) {
                    LOGGER.warn("No room actor preview factory is registered for {}; using portrait fallback", appearance.actorKey)
                }
                renderPortrait(context, playerId, ai, x, y, cardWidth)
            }
        }
    }

    private fun windText(wind: Wind): Text = Text.translatable(
        when (wind) {
            Wind.EAST -> MinecraftMessageKeys.TILE_HONOR_EAST
            Wind.SOUTH -> MinecraftMessageKeys.TILE_HONOR_SOUTH
            Wind.WEST -> MinecraftMessageKeys.TILE_HONOR_WEST
            Wind.NORTH -> MinecraftMessageKeys.TILE_HONOR_NORTH
        },
    )

    /** 對局中面板目前的欄數／列數；固定鎖最多兩欄，窄視窗時東南西北改成 2×2 排列。 */
    private fun playingGrid(): MemberGridLayout = memberGridLayout(resolvePlayingPlayerInfo().size, maxColumns = 2)

    /** 每一列（grid row）可用的垂直高度；只有一列時維持原本佔滿到畫面底部的行為，多列時平分剩餘高度。 */
    private fun playingRowHeight(): Int {
        val rows = playingGrid().rows
        val available = (height - 38) - MEMBER_CARD_TOP
        return if (rows <= 1) available else available / rows
    }

    private fun playingRowTop(row: Int): Int = MEMBER_CARD_TOP + row * playingRowHeight()

    /** 該列資訊清單的起始 Y；與卡片頂端的固定間距和單列版面時相同。 */
    private fun playingInfoTop(row: Int): Int = playingRowTop(row) + (PLAYING_INFO_TOP - MEMBER_CARD_TOP)

    private fun playingCardBottom(row: Int): Int = (playingRowTop(row) + playingRowHeight())
        .coerceAtLeast(playingInfoTop(row) + PLAYING_INFO_ROW_HEIGHT + 4)

    /** 兩列共用同一個捲動位置，可見行數以單一列的可用高度為準。 */
    private fun visiblePlayingInfoRows(): Int = ((playingRowHeight() - (PLAYING_INFO_TOP - MEMBER_CARD_TOP) - 4) / PLAYING_INFO_ROW_HEIGHT).coerceAtLeast(1)

    /** 目前進行中對局資訊清單的完整行數，供 scrollbar 幾何與捲動上限共用。 */
    private fun totalPlayingInfoRows(): Int {
        val dealerPlayerId = resolvePlayerInfoEntity()?.dealerPlayerId ?: stateStore.gameSnapshot?.dealerPlayerId
        return resolvePlayingPlayerInfo().maxOfOrNull { playingInfoRows(it, dealerPlayerId).size } ?: 0
    }

    private fun maximumPlayingInfoScroll(): Int = (totalPlayingInfoRows() - visiblePlayingInfoRows()).coerceAtLeast(0)

    /** 建立指定列進行中對局資訊 scrollbar 的共用幾何，供繪製與拖曳换算使用同一份座標系。 */
    private fun playingInfoScrollbarLayout(row: Int): ScrollbarLayout = ScrollbarLayout(
        trackTop = playingInfoTop(row),
        trackBottom = playingCardBottom(row),
        itemCount = totalPlayingInfoRows(),
        visibleItemCount = visiblePlayingInfoRows(),
        scrollIndex = playingInfoScroll.index,
        minimumThumbHeight = 12,
    )

    /** 每一列各自畫一段 scrollbar，共用同一個捲動位置。 */
    private fun renderPlayingInfoScrollbar(context: DrawContext, maximumScroll: Int) {
        if (maximumScroll == 0) return
        (0 until playingGrid().rows).forEach { row ->
            val layout = playingInfoScrollbarLayout(row)
            context.fill(width - 14, layout.trackTop, width - 9, layout.trackBottom, 0x80505050.toInt())
            context.fill(width - 14, layout.thumbTop, width - 9, layout.thumbTop + layout.thumbHeight, 0xFFD0D0D0.toInt())
        }
    }

    /** 游標所在的那一列 scrollbar 索引；不在任何一列的 scrollbar 範圍內時為 null。 */
    private fun playingInfoScrollbarRowAt(mouseX: Double, mouseY: Double): Int? {
        if (maximumPlayingInfoScroll() <= 0) return null
        if (mouseX < width - 18 || mouseX > width - 5) return null
        return (0 until playingGrid().rows).firstOrNull { row -> mouseY >= playingInfoTop(row) && mouseY <= playingCardBottom(row) }
    }

    private fun renderPortrait(context: DrawContext, playerId: Uuid, isAi: Boolean, x: Int, y: Int, cardWidth: Int) {
        val portraitSize = (cardWidth - 24).coerceIn(28, 38)
        portraitRenderer.render(
            playerId,
            isAi,
            (x + (cardWidth - portraitSize) / 2).toFloat(),
            (y + 24).toFloat(),
            portraitSize.toFloat(),
            1f,
            200f,
            context.matrices,
            MinecraftClient.getInstance().bufferBuilders.entityVertexConsumers,
        )
    }

    private fun renderSettings(context: DrawContext, mouseX: Int, mouseY: Int): Text? {
        val config = draftConfig ?: currentConfig() ?: return null
        val resolved = configResolver.resolve(config)
        val definition = resolved.definition
        val fieldsTop = settingsFieldsTop()
        if (definition == null) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable(MinecraftRoomScreenKeys.READ_ONLY),
                width / 2,
                fieldsTop,
                0xAAAAAA,
            )
            return null
        }
        var hoveredLabel: Text? = null
        val compact = isCompactSettings()
        val rowHeight = fieldRowHeight()
        definition.fields.filter { it.categoryId == selectedCategoryId }
            .drop(fieldScroll.index)
            .take(maximumVisibleFields())
            .forEachIndexed { index, field ->
                val label = Text.translatable(field.nameTranslationKey)
                val rowTop = fieldsTop + index * rowHeight
                val labelX: Int
                val labelY: Int
                val hoverRight: Int
                val maximumLabelWidth: Int
                if (compact) {
                    labelX = COMPACT_CONTENT_MARGIN
                    labelY = rowTop
                    hoverRight = width - COMPACT_CONTENT_MARGIN
                    maximumLabelWidth = (hoverRight - labelX).coerceAtLeast(1)
                } else {
                    labelX = SETTINGS_FIELD_LABEL_X
                    labelY = rowTop + (20 - VANILLA_VISIBLE_TEXT_HEIGHT) / 2
                    hoverRight = fieldControlLeft(field)
                    maximumLabelWidth = (hoverRight - labelX - SETTINGS_FIELD_LABEL_GAP).coerceAtLeast(1)
                }
                val fittedLabel = fitText(label, maximumLabelWidth)
                context.drawTextWithShadow(textRenderer, fittedLabel, labelX, labelY, 0xFFFFFF)
                if (
                    fittedLabel.string != label.string &&
                    mouseX in labelX until hoverRight &&
                    mouseY in labelY until labelY + textRenderer.fontHeight
                ) {
                    hoveredLabel = label
                }
            }
        renderScrollbar(context, definition.fields.count { it.categoryId == selectedCategoryId })
        val status = when {
            !definition.selectable -> Text.translatable(definition.unavailableReasonTranslationKey!!) to 0xFF7777
            draftStale -> Text.translatable(MinecraftRoomScreenKeys.DRAFT_STALE) to 0xFF5555
            validationFailed -> Text.translatable(MinecraftRoomScreenKeys.VALIDATION_FAILED) to 0xFF5555
            else -> null
        }
        status?.let { (message, color) ->
            val statusLabelX = if (compact) COMPACT_CONTENT_MARGIN else SETTINGS_FIELD_LABEL_X
            val contentWidth = (width - statusLabelX - 20).coerceAtLeast(1)
            context.drawCenteredTextWithShadow(
                textRenderer,
                fitText(message, contentWidth),
                statusLabelX + contentWidth / 2,
                settingsStatusY(),
                color,
            )
        }
        return hoveredLabel
    }

    private fun currentConfig(): GameConfig? = stateStore.roomSnapshot?.gameConfig
        ?: stateStore.tableLobby?.playingGameConfig?.let { dto -> dto.toDomain(networkRegistries) }

    private fun memberName(playerId: Uuid, ai: Boolean, orderedAiPlayerIds: List<Uuid>): Text {
        if (ai) return Text.literal(aiPlayerDisplayName(playerId, orderedAiPlayerIds))
        val name = client?.networkHandler?.getPlayerListEntry(playerId.toJavaUuid())?.profile?.name
        return Text.literal(name ?: playerId.toString().take(8))
    }

    /**
     * 依玩家數與目前寬度計算卡片改用幾欄幾列。換列的判斷門檻是卡片能不能維持在
     * [MEMBER_CARD_MIN_WIDTH] 以上，不是能不能維持在理想的 [MEMBER_CARD_MAX_WIDTH]——同一列本來就會
     * 把卡片縮到剛好塞滿寬度，只要還在可讀範圍內就不需要換列。[maxColumns] 讓對局中固定 4 人的面板
     * 可以鎖定最多兩欄。
     */
    private fun memberGridLayout(playerCount: Int, maxColumns: Int = Int.MAX_VALUE): MemberGridLayout {
        val count = playerCount.coerceAtLeast(1)
        val availableWidth = width - MEMBER_GRID_MARGIN * 2
        val fittingColumns = (availableWidth / MEMBER_CARD_MIN_WIDTH).coerceAtLeast(1)
        val columns = minOf(count, fittingColumns, maxColumns)
        val cardWidth = minOf(MEMBER_CARD_MAX_WIDTH, availableWidth / columns)
        val rows = (count + columns - 1) / columns
        return MemberGridLayout(columns, cardWidth, rows)
    }

    private data class MemberGridLayout(val columns: Int, val cardWidth: Int, val rows: Int)

    /** 保留底部操作列後，等待室玩家卡片 grid 可用的下界。 */
    private fun memberGridBottom(): Int = height - MEMBER_GRID_BOTTOM_RESERVED_HEIGHT

    /** 目前視窗高度能完整顯示幾列玩家卡片。 */
    private fun memberGridVisibleRows(): Int = ((memberGridBottom() - MEMBER_CARD_TOP) / MEMBER_ROW_HEIGHT).coerceAtLeast(1)

    /** 建立等待室玩家卡片 grid scrollbar 的共用幾何，供繪製與拖曳换算使用同一份座標系。 */
    private fun memberGridScrollbarLayout(rows: Int): ScrollbarLayout = ScrollbarLayout(
        trackTop = MEMBER_CARD_TOP,
        trackBottom = memberGridBottom(),
        itemCount = rows,
        visibleItemCount = memberGridVisibleRows(),
        scrollIndex = memberScroll.index,
        minimumThumbHeight = 12,
    )

    private fun isOverMemberGridScrollbar(mouseX: Double, mouseY: Double, rows: Int): Boolean = rows > memberGridVisibleRows() &&
        mouseX >= width - 18 &&
        mouseX <= width - 5 &&
        mouseY >= MEMBER_CARD_TOP &&
        mouseY <= memberGridBottom()

    /** 等待室目前的卡片 grid 版面；不在等待室（沒有房間快照或還沒進入等待階段）時為 null。 */
    private fun memberGridForWaitingRoom(): MemberGridLayout? {
        if (stateStore.tableLobby?.phase != TableLobbyPhaseDto.WAITING) return null
        val room = stateStore.roomSnapshot ?: return null
        return memberGridLayout(room.playerIds.size)
    }

    /** 保留底部操作列後目前視窗能容納的設定欄位數。 */
    private fun settingsContentBottom(): Int = height - SETTINGS_BOTTOM_RESERVED_HEIGHT

    private fun settingsStatusY(): Int = height - SETTINGS_STATUS_BOTTOM_OFFSET

    private fun maximumVisibleFields(): Int = ((settingsContentBottom() - settingsFieldsTop()) / fieldRowHeight()).coerceAtLeast(1)

    /** 視窗寬度不足以容納側邊欄、label 與控制項並排時，改用堆疊版面。 */
    private fun isCompactSettings(): Boolean = width < COMPACT_SETTINGS_MIN_WIDTH

    /** 目前規則模組的設定分類數，供 compact 版面計算頂部分類格所需高度。 */
    private fun currentCategoryCount(): Int = (draftConfig ?: currentConfig())?.let(configResolver::resolve)
        ?.definition?.categories?.size ?: 0

    /** Compact 版面下規則切換鈕與分類格佔用內容區頂部空間，欄位列必須從其下方開始。 */
    private fun settingsFieldsTop(): Int = if (isCompactSettings()) {
        val categoryRows = (currentCategoryCount() + 1) / 2
        COMPACT_CATEGORY_TOP + categoryRows * COMPACT_CATEGORY_ROW_HEIGHT + COMPACT_FIELDS_GAP
    } else {
        SETTINGS_FIELDS_TOP
    }

    /** Compact 版面 label 與控制項改上下堆疊，單一欄位列需要更高的垂直空間。 */
    private fun fieldRowHeight(): Int = if (isCompactSettings()) COMPACT_FIELD_ROW_HEIGHT else 28

    /**
     * 控制項在該欄位列中的實際 Y 座標；compact 版面下 label 畫在 [rowTop]，控制項必須讓出 label 的高度
     * 才不會疊在一起，非 compact 版面則與 label 同高並排，維持既有版面。
     */
    private fun fieldControlY(rowTop: Int): Int = if (isCompactSettings()) rowTop + COMPACT_CONTROL_Y_OFFSET else rowTop

    /** 依 editor 實際控制元件的左界計算本地化標籤可用寬度。 */
    private fun fieldControlLeft(field: GameConfigFieldDefinition): Int = when (field.editor) {
        GameConfigEditorSpec.BooleanToggle -> width - 152
        is GameConfigEditorSpec.SingleChoice,
        is GameConfigEditorSpec.IntegerInput,
        -> width - 212
    }

    /** 目前分類的完整欄位數，供 scrollbar 幾何與捲動上限共用。 */
    private fun currentFieldCount(): Int = (draftConfig ?: currentConfig())?.let(configResolver::resolve)
        ?.definition?.fields?.count { it.categoryId == selectedCategoryId } ?: 0

    private fun maximumFieldScroll(): Int = (currentFieldCount() - maximumVisibleFields()).coerceAtLeast(0)

    /** 建立設定欄位 scrollbar 的共用幾何，供繪製與拖曳换算使用同一份座標系。 */
    private fun fieldScrollbarLayout(fieldCount: Int = currentFieldCount()): ScrollbarLayout = ScrollbarLayout(
        trackTop = settingsFieldsTop(),
        trackBottom = settingsContentBottom(),
        itemCount = fieldCount,
        visibleItemCount = maximumVisibleFields(),
        scrollIndex = fieldScroll.index,
        minimumThumbHeight = 12,
    )

    /** 只在內容超出可見範圍時繪製可拖曳 scrollbar。 */
    private fun renderScrollbar(context: DrawContext, fieldCount: Int) {
        val layout = fieldScrollbarLayout(fieldCount)
        if (layout.maximumScroll <= 0) return
        context.fill(width - 14, layout.trackTop, width - 9, layout.trackBottom, 0x80505050.toInt())
        context.fill(width - 14, layout.thumbTop, width - 9, layout.thumbTop + layout.thumbHeight, 0xFFD0D0D0.toInt())
    }

    private fun isOverScrollbar(mouseX: Double, mouseY: Double): Boolean = mouseX >= width - 18 &&
        mouseX <= width - 5 &&
        mouseY >= settingsFieldsTop() &&
        mouseY <= settingsContentBottom()

    /** 依實際像素寬度截斷成員名稱並補省略號。 */
    private fun fitText(text: Text, maximumWidth: Int): Text {
        if (maximumWidth <= 0) return Text.empty()
        if (textRenderer.getWidth(text) <= maximumWidth) return text
        val raw = text.string
        val suffix = "..."
        var end = raw.length
        while (end > 0 && textRenderer.getWidth(raw.substring(0, end) + suffix) > maximumWidth) end--
        return Text.literal(raw.substring(0, end) + suffix)
    }

    /** 優先使用世界中的真人；不在載入範圍時以 player-list profile 建立純客戶端預覽。 */
    private fun resolvePlayerPreview(playerId: Uuid): LivingEntity? {
        val minecraft = client ?: return null
        minecraft.world?.getPlayerByUuid(playerId.toJavaUuid())?.let { return it }
        val world = minecraft.world ?: return null
        val profile = minecraft.networkHandler?.getPlayerListEntry(playerId.toJavaUuid())?.profile ?: return null
        return profilePreviews.getOrPut(playerId) { OtherClientPlayerEntity(world, profile) }
    }

    private fun booleanText(enabled: Boolean): Text = Text.translatable(
        if (enabled) MinecraftRoomScreenKeys.TRUE else MinecraftRoomScreenKeys.FALSE,
    )

    private fun optionText(optionId: String): Text = Text.translatable(MinecraftRoomScreenKeys.configOption(optionId))

    private fun integerText(number: Int?, unit: String?): Text = when {
        number == null -> Text.translatable(MinecraftRoomScreenKeys.NONE)
        unit == null -> Text.literal(formatInteger(number))
        else -> Text.translatable(unit, number)
    }

    /** Tooltip 使用固定且不受系統語系影響的千分位，輸入框仍保留純整數。 */
    private fun formatInteger(number: Int): String = String.format(Locale.ROOT, "%,d", number)

    /**
     * Esc 在設定頁沒有可套用草稿時直接返回玩家頁；有尚未套用的合法草稿時改顯示「套用並返回／放棄
     * 變更／繼續編輯」確認畫面，避免無聲丟棄——房間規則是所有玩家共享的狀態，比個人化的用戶端設定
     * 風險更高（比照 [MahjongClientConfigScreen]／[MahjongHudLayoutEditorScreen] 既有的保護）。玩家頁
     * 才離開整個畫面。
     */
    override fun close() {
        if (page == Page.SETTINGS) {
            if (hasUnsavedSettingsDraft()) {
                client?.setScreen(
                    UnsavedChangesConfirmationScreen(
                        this,
                        {
                            finishSettings()
                            client?.setScreen(this)
                        },
                        {
                            restoreAuthoritativeDraft()
                            page = Page.ROOM
                            client?.setScreen(this)
                        },
                        gameConfigDifferenceText(configResolver, ruleNames, currentConfig()!!, draftConfig!!),
                    ),
                )
            } else {
                restoreAuthoritativeDraft()
                page = Page.ROOM
                rebuild()
            }
        } else {
            closeEntireScreen()
        }
    }

    /**
     * 目前是否有尚未套用、且可以直接套用的設定草稿；草稿因外部變更失效或驗證失敗時視為沒有，交由
     * 既有 Undo 流程處理，不提供「套用並返回」選項。
     */
    private fun hasUnsavedSettingsDraft(): Boolean = !draftStale && !validationFailed && draftConfig != null && draftConfig != currentConfig()

    /** 關閉整個 RoomScreen，不套用設定頁的階層式 Esc 行為。 */
    private fun closeEntireScreen() {
        client?.setScreen(null)
    }

    /** 在目前輸入事件完成後安全重建 widgets，避免舊 widget 被重新設為 focus。 */
    private fun rebuild() {
        rebuildRequested = true
    }

    override fun shouldPause(): Boolean = false

    override fun removed() {
        super.removed()
        stateStore.tableLobby?.let { lobby ->
            MahjongChannels.roomScreenAction.sendToServer(json, RoomScreenActionDto.Close(lobby.tableId))
        }
    }

    private enum class Page { ROOM, SETTINGS }

    private data class ActionButton(
        val key: String,
        val action: RoomScreenActionDto,
        val active: Boolean,
    )

    private sealed interface MemberStatus {
        data class Waiting(
            val readyPlayerIds: Set<Uuid>,
            val hostId: Uuid,
        ) : MemberStatus

        data object Playing : MemberStatus
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger(RoomScreen::class.java)
        const val SETTINGS_FIELDS_TOP = 54
        const val SETTINGS_FIELD_LABEL_X = 150
        const val SETTINGS_FIELD_LABEL_GAP = 8
        const val SETTINGS_BOTTOM_RESERVED_HEIGHT = 56
        const val SETTINGS_STATUS_BOTTOM_OFFSET = 47
        const val VANILLA_VISIBLE_TEXT_HEIGHT = 8

        /** 視窗寬度低於此值時，設定頁側邊欄／label／控制項改為窄視窗堆疊版面。 */
        const val COMPACT_SETTINGS_MIN_WIDTH = 400
        const val COMPACT_CONTENT_MARGIN = 18
        const val COMPACT_CATEGORY_TOP = 78
        const val COMPACT_CATEGORY_ROW_HEIGHT = 24
        const val COMPACT_FIELDS_GAP = 8
        const val COMPACT_FIELD_ROW_HEIGHT = 42

        /** Label 與控制項之間的垂直間距；控制項的 Y 座標要在 label 下方讓出這麼多空間。 */
        const val COMPACT_CONTROL_Y_OFFSET = 14
        const val COMPACT_INTEGER_BUTTON_WIDTH = 24
        const val COMPACT_INTEGER_BUTTON_GAP = 4

        /** 右側需保留給 scrollbar 的寬度，避免 compact 控制項覆蓋到它。 */
        const val COMPACT_SCROLLBAR_RESERVE = 20
        const val MIN_COMPACT_CONTROL_WIDTH = 80
        const val MEMBER_CARD_TOP = 58
        const val MEMBER_NAME_OFFSET = 96
        const val MEMBER_CARD_BACKGROUND = 0xA0202838.toInt()

        /** 卡片 grid 左右各自的邊距。 */
        const val MEMBER_GRID_MARGIN = 8

        /** 卡片渲染上限寬度，同一列有多餘空間時最多縮放到這個寬度。 */
        const val MEMBER_CARD_MAX_WIDTH = 126

        /** 卡片可讀性下限；同一列縮到低於這個寬度才需要換列，而不是一達到最大寬度就換列。 */
        const val MEMBER_CARD_MIN_WIDTH = 70

        /** 等待室卡片換列時，每列（grid row）佔用的垂直高度。 */
        const val MEMBER_ROW_HEIGHT = 156

        /** 等待室卡片 grid 保留給底部操作列的高度；grid 超出這個範圍時改用捲動而不是疊上去。 */
        const val MEMBER_GRID_BOTTOM_RESERVED_HEIGHT = 40

        /** AI 策略按鈕相對卡片頂端（[MEMBER_CARD_TOP]）的垂直偏移。 */
        const val AI_STRATEGY_BUTTON_OFFSET_Y = 126

        /** 踢出按鈕相對卡片頂端（[MEMBER_CARD_TOP]）的垂直偏移。 */
        const val KICK_BUTTON_OFFSET_Y = 4
        const val PLAYING_INFO_TOP = 170
        const val PLAYING_INFO_ROW_HEIGHT = 12
        val WIND_ORDER = mapOf(Wind.EAST to 0, Wind.SOUTH to 1, Wind.WEST to 2, Wind.NORTH to 3)
    }
}
