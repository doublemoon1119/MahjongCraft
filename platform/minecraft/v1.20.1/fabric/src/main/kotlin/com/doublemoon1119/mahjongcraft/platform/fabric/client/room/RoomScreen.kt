package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomScreenActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.TableLobbyPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollState
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.SettingsFooterLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.UnsavedChangesConfirmationScreen
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerProfileResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.resolvedPlayerNameText
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.OfflinePlayerPreviewEntity
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
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoEntry
import com.mojang.authlib.GameProfile
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
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/** 空桌、等待房間與進行中對局共用的桌級畫面。 */
class RoomScreen(
    private val stateStore: ClientMahjongStateStore,
    private val tableId: Uuid,
    private val configPresentations: GameConfigPresentationRegistry,
    private val configResolver: GameConfigPresentationResolver,
    private val ruleNames: RuleModuleDisplayNameRegistry,
    private val portraitRenderer: PlayerPortraitRenderer,
    private val aiStrategies: MahjongAiStrategyRegistry,
    private val aiStrategyNames: AiStrategyDisplayNameRegistry,
    appearanceSources: RoomMemberAppearanceSourceRegistry,
    indicatorTextResolver: PublicPlayerIndicatorTextResolver,
    private val json: Json,
    private val networkRegistries: NetworkDtoRegistries,
    private val profileResolver: ClientPlayerProfileResolver,
    openSettings: Boolean = false,
) : Screen(Text.translatable(MinecraftRoomScreenKeys.TITLE)) {
    private var page = if (openSettings) Page.SETTINGS else Page.ROOM
    private var selectedCategoryId: String? = null
    private val fieldScroll = ScrollState()
    private val draft = RoomSettingsDraft()
    private val playingInfoScroll = ScrollState()

    /** 目前正在拖曳哪一列（grid row）的對局資訊 scrollbar；兩列共用同一個捲動位置，但幾何各自獨立。 */
    private var playingInfoDragRow = 0

    /** 等待室玩家卡片 grid 換列時的垂直捲動狀態，避免超出範圍的列疊在底部操作列上。 */
    private val memberScroll = ScrollState()
    private var applyButton: ButtonWidget? = null
    private var undoButton: ButtonWidget? = null
    private var resetButton: ButtonWidget? = null
    private var doneButton: ButtonWidget? = null
    private var rebuildRequested = false
    private var lastRoomSnapshot = stateStore.roomSnapshot(tableId)
    private var lastLobby = stateStore.tableLobby(tableId)
    private var wasWaitingRoomMember = stateStore.tableLobby(tableId)?.phase == TableLobbyPhaseDto.WAITING && stateStore.roomSnapshot(tableId)?.isInRoom == true
    private val profilePreviews = mutableMapOf<Uuid, OtherClientPlayerEntity>()
    private val appearanceResolver = RoomMemberAppearanceResolver(appearanceSources)
    private val memberPresentation = RoomMemberPresentation(indicatorTextResolver)

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
        val lobby = stateStore.tableLobby(tableId) ?: return
        val room = stateStore.roomSnapshot(tableId)
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
                } else {
                    val selfId = client?.player?.uuid?.let(UUID::toString)
                    val ready = room.readyPlayerIds.any { it.toString() == selfId }
                    button(
                        if (ready) MinecraftRoomScreenKeys.CANCEL_READY else MinecraftRoomScreenKeys.READY,
                        RoomScreenActionDto.ToggleReady(lobby.tableId),
                    )
                    button(MinecraftRoomScreenKeys.LEAVE, RoomScreenActionDto.Leave(lobby.tableId))
                }
                addMemberCardWidgets(lobby.tableId, room)
                addCenteredActions(actions, bottom)
            }
        }
    }

    /**
     * 在等待室的成員卡片上加入 AI 策略與踢除按鈕。
     *
     * AI 策略對所有觀看者都顯示，非房主取得的是不可點擊的版本——策略是房間的公開資訊，只有變更它需要
     * 房主身分。踢除按鈕只有房主才有。房主本人的卡片兩者都不加。
     */
    private fun addMemberCardWidgets(tableIdText: String, room: RoomSnapshot) {
        val grid = memberGrid(room.playerIds.size)
        memberScroll.clamp(grid.rows - grid.visibleRows)
        val visibleRows = grid.visibleGridRows
        val gridWidth = grid.columns * grid.cardWidth
        room.playerIds.forEachIndexed { index, targetId ->
            if (targetId == room.hostId) return@forEachIndexed
            val row = index / grid.columns
            if (row !in visibleRows) return@forEachIndexed
            val cardX = width / 2 - gridWidth / 2 + (index % grid.columns) * grid.cardWidth
            val cardY = grid.cardTop(row)
            if (targetId in room.aiPlayerIds) {
                val current = room.aiPlayerStrategyKeys[targetId]
                addDrawableChild(
                    RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftRoomScreenKeys.AI_STRATEGY, aiStrategyText(current))) {
                        val keys = aiStrategies.getAllStrategyKeys().sorted()
                        if (keys.isNotEmpty()) {
                            val next = keys[(keys.indexOf(current).coerceAtLeast(0) + 1) % keys.size]
                            send(RoomScreenActionDto.ChangeAiStrategy(tableIdText, targetId.toString(), next))
                        }
                    }.dimensions(cardX + 8, cardY + AI_STRATEGY_BUTTON_OFFSET_Y, grid.cardWidth - 20, 18).build().also {
                        it.tooltip = Tooltip.of(aiStrategyTooltip(current))
                        it.active = room.isHost
                    },
                )
            }
            if (!room.isHost) return@forEachIndexed
            addDrawableChild(
                RestartableMarqueeButtonWidget.builder(Text.literal("×")) {
                    send(RoomScreenActionDto.Kick(tableIdText, targetId.toString()))
                }.dimensions(cardX + grid.cardWidth - 23, cardY + KICK_BUTTON_OFFSET_Y, 16, 16).build().also {
                    it.tooltip = Tooltip.of(Text.translatable(MinecraftRoomScreenKeys.KICK))
                },
            )
        }
    }

    private fun initSettingsPage() {
        val authoritative = currentConfig() ?: return
        val room = stateStore.roomSnapshot(tableId)
        draft.beginIfAbsent(authoritative)
        val config = draft.config ?: authoritative
        val resolved = configResolver.resolve(config)
        val moduleId = resolved.ruleModuleId
        val definition = resolved.definition ?: return
        val canEditRoom = stateStore.tableLobby(tableId)?.phase == TableLobbyPhaseDto.WAITING && room?.isHost == true
        val editable = canEditRoom && definition.selectable
        val categories = definition.categories
        if (selectedCategoryId !in categories.map { it.id }) selectedCategoryId = categories.firstOrNull()?.id
        val compact = settingsLayout().isCompact
        addRuleSelector(config, moduleId, canEditRoom, compact)
        if (compact) {
            val categoryWidth = (width - COMPACT_CONTENT_MARGIN * 2 - RoomSettingsLayout.COMPACT_FIELDS_GAP) / 2
            categories.forEachIndexed { index, category ->
                addDrawableChild(
                    RestartableMarqueeButtonWidget.builder(Text.translatable(category.nameTranslationKey)) {
                        selectedCategoryId = category.id
                        fieldScroll.reset()
                        rebuild()
                    }.dimensions(
                        COMPACT_CONTENT_MARGIN + index % 2 * (categoryWidth + RoomSettingsLayout.COMPACT_FIELDS_GAP),
                        RoomSettingsLayout.COMPACT_CATEGORY_TOP + index / 2 * RoomSettingsLayout.COMPACT_CATEGORY_ROW_HEIGHT,
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
        val settings = settingsLayout()
        val maximumVisibleFields = settings.maximumVisibleFields
        fieldScroll.clamp(categoryFields.size - maximumVisibleFields)
        val fieldsTop = settings.fieldsTop
        val rowHeight = settings.fieldRowHeight
        categoryFields.drop(fieldScroll.index).take(maximumVisibleFields).forEachIndexed { index, field ->
            val controlY = settings.fieldControlY(fieldsTop + index * rowHeight)
            addFieldControls(field, config, controlY, editable && field.isEditable && field.isEnabled(config))
        }
        if (editable) {
            val defaultConfig = draft.normalized(GameConfig(definition.defaultRuleConfig()))
            val footer = settingsFooterLayout()
            val reset = RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftRoomScreenKeys.RESET_DEFAULTS_BUTTON)) {
                draft.resetTo(defaultConfig)
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
                it.active = draft.canDone()
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
        val compact = settingsLayout().isCompact
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
                        widget.setEditableColor(if (draft.isFieldInvalid(field.id)) 0xFF5555 else 0xFFFFFF)
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
        draft.updateField(field, value)
        rebuild()
    }

    /** 直接輸入整數時保留焦點，只更新草稿與驗證狀態。 */
    private fun updateNumericDraft(
        field: GameConfigFieldDefinition,
        editor: GameConfigEditorSpec.IntegerInput,
        raw: String,
    ) {
        if (!draft.markNumericInput(field, editor, raw)) {
            refreshDraftButtons()
            return
        }
        draft.applyNumericInput(field, raw)
        refreshDraftButtons()
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
                draft.resetTo(config.copy(ruleConfig = next.defaultRuleConfig()))
                selectedCategoryId = null
                fieldScroll.reset()
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
                        formatRoomInteger(editor.minimum),
                        formatRoomInteger(editor.maximum),
                    ).formatted(Formatting.GOLD),
                )
                result.append("\n• ").append(
                    Text.translatable(MinecraftRoomScreenKeys.NORMAL_STEP, formatRoomInteger(editor.step)).formatted(Formatting.WHITE),
                )
                result.append("\n• ").append(
                    Text.translatable(MinecraftRoomScreenKeys.SHIFT_STEP, formatRoomInteger(editor.step * 10)).formatted(Formatting.YELLOW),
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
        if (!draft.canDone()) return
        val lobby = stateStore.tableLobby(tableId) ?: return
        val config = draft.config ?: return
        MahjongChannels.roomScreenAction.sendToServer(json, RoomScreenActionDto.UpdateConfig(lobby.tableId, config.toDto(networkRegistries)))
    }

    /** 有變更時提交並等待權威 snapshot，沒有變更時立即返回玩家頁。 */
    private fun finishSettings() {
        val authoritative = currentConfig() ?: return
        val current = draft.config ?: authoritative
        if (current == authoritative) {
            page = Page.ROOM
            rebuild()
            return
        }
        draft.markReturnToRoomAfterApply()
        applyDraft()
    }

    /** 將草稿恢復成目前權威設定。 */
    private fun restoreAuthoritativeDraft(authoritative: GameConfig? = currentConfig()) {
        val resolvedAuthoritative = authoritative ?: return
        draft.restoreAuthoritative(resolvedAuthoritative)
        rebuild()
    }

    /** 依草稿、權威值與預設值同步儲存、取消及重設按鈕狀態。 */
    private fun refreshDraftButtons(
        current: GameConfig? = draft.config,
        authoritative: GameConfig? = currentConfig(),
        defaults: GameConfig? = current?.let { config ->
            configResolver.resolve(config).definition?.let { GameConfig(it.defaultRuleConfig()) }
        },
    ) {
        applyButton?.active = draft.canApply(authoritative)
        undoButton?.active = draft.canUndo(authoritative)
        resetButton?.active = draft.canReset(defaults)
        doneButton?.active = draft.canDone()
        val changes = if (current != null && authoritative != null && current != authoritative && !draft.isStale) {
            Tooltip.of(gameConfigDifferenceText(configResolver, ruleNames, authoritative, current))
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
            if (fieldScroll.scrollBy(amount, settingsLayout().maximumScroll)) rebuild()
            return true
        }
        val playingInfoScrollMaximum = playingGrid().maximumInfoScroll(totalPlayingInfoRows())
        if (page == Page.ROOM && stateStore.tableLobby(tableId)?.phase == TableLobbyPhaseDto.PLAYING && playingInfoScrollMaximum > 0) {
            playingInfoScroll.scrollBy(amount, playingInfoScrollMaximum)
            return true
        }
        val grid = currentMemberGrid()
        if (page == Page.ROOM && grid != null) {
            if (memberScroll.scrollBy(amount, (grid.rows - grid.visibleRows).coerceAtLeast(0))) rebuild()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (page == Page.SETTINGS && button == 0 && settingsLayout().isOverScrollbar(mouseX, mouseY)) {
            if (fieldScroll.beginDrag(mouseY, settingsLayout().scrollbar)) rebuild()
            return true
        }
        if (page == Page.ROOM && button == 0) {
            val playingGrid = playingGrid()
            val totalInfoRows = totalPlayingInfoRows()
            val row = playingGrid.infoScrollbarRowAt(mouseX, mouseY, totalInfoRows)
            if (row != null) {
                playingInfoDragRow = row
                playingInfoScroll.beginDrag(mouseY, playingGrid.infoScrollbar(row, totalInfoRows, playingInfoScroll.index))
                return true
            }
            val grid = currentMemberGrid()
            if (grid != null && grid.isOverGridScrollbar(mouseX, mouseY)) {
                if (memberScroll.beginDrag(mouseY, grid.scrollbar)) rebuild()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (fieldScroll.dragging && button == 0) {
            if (fieldScroll.dragTo(mouseY, settingsLayout().scrollbar)) rebuild()
            return true
        }
        if (playingInfoScroll.dragging && button == 0) {
            playingInfoScroll.dragTo(mouseY, playingGrid().infoScrollbar(playingInfoDragRow, totalPlayingInfoRows(), playingInfoScroll.index))
            return true
        }
        if (memberScroll.dragging && button == 0) {
            val grid = currentMemberGrid()
            if (grid != null && memberScroll.dragTo(mouseY, grid.scrollbar)) rebuild()
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
        if (!isTableStillAvailable()) {
            closeEntireScreen()
            return
        }
        if (stateStore.tableLobby(tableId) != lastLobby || stateStore.roomSnapshot(tableId) != lastRoomSnapshot) {
            val previousLobby = lastLobby
            val previousRoom = lastRoomSnapshot
            val currentLobby = stateStore.tableLobby(tableId)
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
            lastLobby = stateStore.tableLobby(tableId)
            lastRoomSnapshot = stateStore.roomSnapshot(tableId)
            if (draft.isReturningToRoomAfterApply && currentConfig() == draft.config) {
                draft.adoptAuthoritative(currentConfig())
                page = Page.ROOM
            }
            if (stateStore.tableLobby(tableId)?.phase == TableLobbyPhaseDto.EMPTY) {
                wasWaitingRoomMember = false
                page = Page.ROOM
                draft.clear()
            }
            clearAndInit()
            return
        }
        val authoritative = currentConfig() ?: return
        when (val outcome = draft.onAuthoritativeChanged(authoritative)) {
            RoomSettingsDraft.Outcome.Unchanged -> Unit
            is RoomSettingsDraft.Outcome.Adopted -> {
                if (outcome.returnToRoom) page = Page.ROOM
                clearAndInit()
            }
            RoomSettingsDraft.Outcome.BecameStale -> refreshDraftButtons()
        }
    }

    /** 離開桌旁或切換維度時關閉畫面，讓 removed() 清除暫時 observer。 */
    /**
     * 這張桌子目前是否仍可使用：玩家在同一個維度、距離夠近，而且桌子本身還在。
     *
     * 桌子可能在畫面開著時被其他玩家破壞，因此距離之外還要確認方塊實體仍然存在；距離條件已經保證所在
     * 區塊是載入的，查不到方塊實體就代表桌子真的沒了。payload 沒有帶座標時無從判斷，一律視為仍可使用。
     */
    private fun isTableStillAvailable(): Boolean {
        val lobby = stateStore.tableLobby(tableId) ?: return false
        val x = lobby.tableX ?: return true
        val y = lobby.tableY ?: return true
        val z = lobby.tableZ ?: return true
        val expectedDimension = lobby.dimensionId ?: return true
        val player = client?.player ?: return false
        val world = client?.world ?: return false
        if (world.registryKey.value.toString() != expectedDimension) return false
        if (player.squaredDistanceTo(x + 0.5, y + 0.5, z + 0.5) > 64.0) return false
        return world.getBlockEntity(BlockPos(x, y, z)) is MahjongTableBlockEntity
    }

    private fun renderRoom(context: DrawContext, mouseX: Int, mouseY: Int) {
        val lobby = stateStore.tableLobby(tableId) ?: return
        when (lobby.phase) {
            TableLobbyPhaseDto.EMPTY -> context.drawCenteredTextWithShadow(textRenderer, Text.translatable(MinecraftRoomScreenKeys.EMPTY), width / 2, 70, 0xFFFFFF)
            TableLobbyPhaseDto.PLAYING -> {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable(MinecraftRoomScreenKeys.PLAYING), width / 2, 50, 0xFFCC55)
                renderPlayingMembers(context, resolvePlayingPlayerInfo(), mouseX, mouseY)
            }
            TableLobbyPhaseDto.WAITING -> stateStore.roomSnapshot(tableId)?.let { room ->
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

    /**
     * 進行中對局依權威自風排序，固定呈現東、南、西、北——卡片本身的大小與位置跟等待室
     * （[renderMembers]）共用同一套固定尺寸 grid 與捲動機制（[memberScroll]），差異只在名稱下方
     * 顯示的是 player info（[playingInfoRows]）而不是加入狀態，且該區塊內容過長時另外用
     * [playingInfoScroll] 捲動。
     */
    private fun renderPlayingMembers(
        context: DrawContext,
        players: List<MahjongPlayerInfoEntry>,
        mouseX: Int,
        mouseY: Int,
    ) {
        val sortedPlayers = players.sortedBy { WIND_ORDER.getValue(it.seatWind) }
        val dealerPlayerId = resolvePlayerInfoEntity()?.dealerPlayerId ?: stateStore.gameSnapshot(tableId)?.dealerPlayerId
        val grid = playingGrid()
        memberScroll.clamp(grid.rows - grid.visibleRows)
        val totalInfoRows = totalPlayingInfoRows()
        playingInfoScroll.clamp(grid.maximumInfoScroll(totalInfoRows))
        val gridWidth = grid.columns * grid.cardWidth
        sortedPlayers.forEachIndexed { index, player ->
            val gridRow = index / grid.columns
            if (gridRow !in grid.visibleGridRows) return@forEachIndexed
            val x = width / 2 - gridWidth / 2 + (index % grid.columns) * grid.cardWidth
            val y = grid.cardTop(gridRow)
            context.fill(x + 2, y, x + grid.cardWidth - 4, y + RoomMemberGridLayout.CARD_HEIGHT, MEMBER_CARD_BACKGROUND)
            renderMemberAppearance(context, player.playerId, player.isAi, x, y, grid.cardWidth, mouseX, mouseY)
            context.drawCenteredTextWithShadow(
                textRenderer,
                fitText(resolvedPlayerNameText(player.playerName), grid.cardWidth - 12),
                x + grid.cardWidth / 2,
                y + MEMBER_NAME_OFFSET,
                0xFFFFFF,
            )
            val infoTop = y + RoomMemberGridLayout.INFO_OFFSET
            memberPresentation.infoRows(player, dealerPlayerId).drop(playingInfoScroll.index).take(grid.visibleInfoRows).forEachIndexed { infoRowIndex, row ->
                context.drawCenteredTextWithShadow(
                    textRenderer,
                    fitText(row.first, grid.cardWidth - 12),
                    x + grid.cardWidth / 2,
                    infoTop + infoRowIndex * RoomMemberGridLayout.INFO_ROW_HEIGHT,
                    row.second,
                )
            }
        }
        renderMemberGridScrollbar(context, grid)
        renderPlayingInfoScrollbar(context, grid, totalInfoRows)
    }

    /** 優先使用 Player Info entity 的完整公開快照；同步尚未抵達時以遊戲快照安全降級。 */
    private fun resolvePlayingPlayerInfo(): List<MahjongPlayerInfoEntry> {
        resolvePlayerInfoEntity()?.players?.takeIf { it.isNotEmpty() }?.let { return it }
        val snapshot = stateStore.gameSnapshot(tableId) ?: return emptyList()
        return roomMemberEntriesFrom(snapshot.players, ::resolveLocalPlayerName)
    }

    private fun resolvePlayerInfoEntity(): MahjongPlayerInfoEntity? {
        val lobby = stateStore.tableLobby(tableId) ?: return null
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
        val grid = memberGrid(playerIds.size)
        memberScroll.clamp(grid.rows - grid.visibleRows)
        val visibleRows = grid.visibleGridRows
        val gridWidth = grid.columns * grid.cardWidth
        playerIds.forEachIndexed { index, playerId ->
            val row = index / grid.columns
            if (row !in visibleRows) return@forEachIndexed
            val x = width / 2 - gridWidth / 2 + (index % grid.columns) * grid.cardWidth
            val y = grid.cardTop(row)
            context.fill(x + 2, y, x + grid.cardWidth - 4, y + RoomMemberGridLayout.CARD_HEIGHT, MEMBER_CARD_BACKGROUND)
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
                context.drawCenteredTextWithShadow(textRenderer, it, x + grid.cardWidth / 2, y + RoomMemberGridLayout.INFO_OFFSET, if (highlighted) 0x88FF88 else 0xAAAAAA)
            }
        }
        renderMemberGridScrollbar(context, grid)
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
        val resolution = appearanceResolver.resolve(playerId, ai)
        when (val warning = resolution.warning) {
            null -> Unit
            is RoomMemberAppearanceResolver.Warning.ProviderFailed ->
                LOGGER.warn("Failed to resolve room member appearance provider {}", warning.providerId, warning.cause)
            is RoomMemberAppearanceResolver.Warning.MissingActorPreviewFactory ->
                LOGGER.warn("No room actor preview factory is registered for {}; using portrait fallback", warning.actorKey)
        }
        // 玩家離線時找不到可預覽的 entity——沒有真人模型可畫，退回畫像，不能什麼都不畫，
        // 讓那一格看起來像沒東西。
        val entity = if (resolution.appearance == RoomMemberAppearanceResolver.Appearance.PlayerModel) {
            resolvePlayerPreview(playerId)
        } else {
            null
        }
        if (entity == null) {
            renderPortrait(context, playerId, ai, x, y, cardWidth)
            return
        }
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

    /** 對局中面板的卡片版面；跟等待室共用同一套換欄規則與捲動機制。 */
    private fun playingGrid(): RoomMemberGridLayout = memberGrid(resolvePlayingPlayerInfo().size)

    /** 目前進行中對局資訊清單的完整行數，供 scrollbar 幾何與捲動上限共用。 */
    private fun totalPlayingInfoRows(): Int {
        val dealerPlayerId = resolvePlayerInfoEntity()?.dealerPlayerId ?: stateStore.gameSnapshot(tableId)?.dealerPlayerId
        return resolvePlayingPlayerInfo().maxOfOrNull { memberPresentation.infoRows(it, dealerPlayerId).size } ?: 0
    }

    /** 只在卡片列超出可見範圍時繪製 grid scrollbar；等待室與進行中對局共用。 */
    private fun renderMemberGridScrollbar(context: DrawContext, grid: RoomMemberGridLayout) {
        if (grid.rows <= grid.visibleRows) return
        val column = grid.scrollbarColumn
        val layout = grid.scrollbar
        context.fill(column.trackLeft, layout.trackTop, column.trackRight, layout.trackBottom, 0x80505050.toInt())
        context.fill(column.trackLeft, layout.thumbTop, column.trackRight, layout.thumbTop + layout.thumbHeight, 0xFFD0D0D0.toInt())
    }

    /** 每一列各自畫一段 scrollbar，共用同一個捲動位置；只畫目前捲動位置下看得到的列。 */
    private fun renderPlayingInfoScrollbar(context: DrawContext, grid: RoomMemberGridLayout, totalInfoRows: Int) {
        if (grid.maximumInfoScroll(totalInfoRows) == 0) return
        val column = grid.scrollbarColumn
        grid.visibleGridRows.forEach { row ->
            val layout = grid.infoScrollbar(row, totalInfoRows, playingInfoScroll.index)
            context.fill(column.trackLeft, layout.trackTop, column.trackRight, layout.trackBottom, 0x80505050.toInt())
            context.fill(column.trackLeft, layout.thumbTop, column.trackRight, layout.thumbTop + layout.thumbHeight, 0xFFD0D0D0.toInt())
        }
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
        val config = draft.config ?: currentConfig() ?: return null
        val resolved = configResolver.resolve(config)
        val definition = resolved.definition
        val settings = settingsLayout()
        val fieldsTop = settings.fieldsTop
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
        val compact = settings.isCompact
        val rowHeight = settings.fieldRowHeight
        definition.fields.filter { it.categoryId == selectedCategoryId }
            .drop(fieldScroll.index)
            .take(settings.maximumVisibleFields)
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
                    hoverRight = settings.fieldControlLeft(field.editor)
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
            draft.isStale -> Text.translatable(MinecraftRoomScreenKeys.DRAFT_STALE) to 0xFF5555
            draft.hasInvalidFields -> Text.translatable(MinecraftRoomScreenKeys.VALIDATION_FAILED) to 0xFF5555
            else -> null
        }
        status?.let { (message, color) ->
            val statusLabelX = if (compact) COMPACT_CONTENT_MARGIN else SETTINGS_FIELD_LABEL_X
            val contentWidth = (width - statusLabelX - 20).coerceAtLeast(1)
            context.drawCenteredTextWithShadow(
                textRenderer,
                fitText(message, contentWidth),
                statusLabelX + contentWidth / 2,
                settings.statusY,
                color,
            )
        }
        return hoveredLabel
    }

    private fun currentConfig(): GameConfig? = stateStore.roomSnapshot(tableId)?.gameConfig
        ?: stateStore.tableLobby(tableId)?.playingGameConfig?.let { dto -> dto.toDomain(networkRegistries) }

    private fun memberName(playerId: Uuid, ai: Boolean, orderedAiPlayerIds: List<Uuid>): Text {
        if (ai) return Text.literal(aiPlayerDisplayName(playerId, orderedAiPlayerIds))
        return resolvedPlayerNameText(resolveLocalPlayerName(playerId))
    }

    /**
     * 真人玩家名稱的本地解析：在線就用 player list 的 profile；不在線時看之前有沒有非同步向 Mojang
     * 查詢過這個 UUID 並已經拿到結果，兩者都沒有就觸發一次查詢並回傳 `null`，交由呼叫端決定顯示什麼。
     */
    private fun resolveLocalPlayerName(playerId: Uuid): String? {
        val name = client?.networkHandler?.getPlayerListEntry(playerId.toJavaUuid())?.profile?.name
            ?: profileResolver.resolvedProfile(playerId)?.name
        if (name == null) profileResolver.requestResolve(playerId)
        return name
    }

    /** 依目前視窗尺寸與玩家數建立成員卡片版面；幾何公式見 [RoomMemberGridLayout]。 */
    private fun memberGrid(playerCount: Int): RoomMemberGridLayout = RoomMemberGridLayout(
        windowWidth = width,
        windowHeight = height,
        playerCount = playerCount,
        scrollIndex = memberScroll.index,
    )

    /**
     * 目前畫面上實際顯示的卡片 grid 版面（等待室或進行中對局）；兩者共用同一套固定列高與
     * [memberScroll] 捲動機制，不在這兩種狀態時為 null。
     */
    private fun currentMemberGrid(): RoomMemberGridLayout? = when (stateStore.tableLobby(tableId)?.phase) {
        TableLobbyPhaseDto.WAITING -> stateStore.roomSnapshot(tableId)?.let { memberGrid(it.playerIds.size) }
        TableLobbyPhaseDto.PLAYING -> playingGrid()
        else -> null
    }

    /** 依目前視窗尺寸與草稿內容建立設定頁版面；幾何公式見 [RoomSettingsLayout]。 */
    private fun settingsLayout(fieldCount: Int = currentFieldCount()): RoomSettingsLayout = RoomSettingsLayout(
        windowWidth = width,
        windowHeight = height,
        categoryCount = resolvedDefinition()?.categories?.size ?: 0,
        fieldCount = fieldCount,
        scrollIndex = fieldScroll.index,
    )

    /** 目前草稿或權威設定解析出的欄位定義；沒有可編輯定義時為 null。 */
    private fun resolvedDefinition(): GameConfigPresentationDefinition? = (draft.config ?: currentConfig())?.let(configResolver::resolve)?.definition

    /** 目前分類的完整欄位數，供 scrollbar 幾何與捲動上限共用。 */
    private fun currentFieldCount(): Int = resolvedDefinition()?.fields?.count { it.categoryId == selectedCategoryId } ?: 0

    /** 只在內容超出可見範圍時繪製可拖曳 scrollbar。 */
    private fun renderScrollbar(context: DrawContext, fieldCount: Int) {
        val settings = settingsLayout(fieldCount)
        val scrollbar = settings.scrollbar
        if (scrollbar.maximumScroll <= 0) return
        val column = settings.scrollbarColumn
        context.fill(column.trackLeft, scrollbar.trackTop, column.trackRight, scrollbar.trackBottom, 0x80505050.toInt())
        context.fill(column.trackLeft, scrollbar.thumbTop, column.trackRight, scrollbar.thumbTop + scrollbar.thumbHeight, 0xFFD0D0D0.toInt())
    }

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

    /**
     * 優先使用世界中的真人；不在載入範圍時以 player-list profile 建立純客戶端預覽；連 player-list
     * 都沒有這個人時，改用 [profileResolver] 非同步向 Mojang 查詢真實名稱與皮膚——查到之前（或最終
     * 查無此人，例如 offline-mode 帳號）先用 [OfflinePlayerPreviewEntity] 畫一個固定黑底問號的替身，
     * 而不是完全不畫。一旦 [profileResolver] 查到結果，下一次呼叫就會改用真人皮膚，不會卡在替身上。
     */
    private fun resolvePlayerPreview(playerId: Uuid): LivingEntity? {
        val minecraft = client ?: return null
        minecraft.world?.getPlayerByUuid(playerId.toJavaUuid())?.let { return it }
        val world = minecraft.world ?: return null
        val listEntry = minecraft.networkHandler?.getPlayerListEntry(playerId.toJavaUuid())
        val profile = listEntry?.profile ?: profileResolver.resolvedProfile(playerId)
        if (profile == null) {
            profileResolver.requestResolve(playerId)
            return profilePreviews.getOrPut(playerId) {
                OfflinePlayerPreviewEntity(world, GameProfile(playerId.toJavaUuid(), OFFLINE_PLAYER_PROFILE_NAME))
            }
        }
        profilePreviews[playerId]?.takeUnless { it is OfflinePlayerPreviewEntity }?.let { return it }
        return OtherClientPlayerEntity(world, profile).also { profilePreviews[playerId] = it }
    }

    private fun booleanText(enabled: Boolean): Text = Text.translatable(
        if (enabled) MinecraftRoomScreenKeys.TRUE else MinecraftRoomScreenKeys.FALSE,
    )

    private fun optionText(optionId: String): Text = Text.translatable(MinecraftRoomScreenKeys.configOption(optionId))

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
                        gameConfigDifferenceText(configResolver, ruleNames, currentConfig()!!, draft.config!!),
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
    private fun hasUnsavedSettingsDraft(): Boolean = draft.canDone() && draft.hasUnsavedChanges(currentConfig())

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
        stateStore.tableLobby(tableId)?.let { lobby ->
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
        const val SETTINGS_FIELD_LABEL_X = 150
        const val SETTINGS_FIELD_LABEL_GAP = 8
        const val VANILLA_VISIBLE_TEXT_HEIGHT = 8
        const val COMPACT_CONTENT_MARGIN = 18
        const val COMPACT_INTEGER_BUTTON_WIDTH = 24
        const val COMPACT_INTEGER_BUTTON_GAP = 4

        /** 右側需保留給 scrollbar 的寬度，避免 compact 控制項覆蓋到它。 */
        const val COMPACT_SCROLLBAR_RESERVE = 20
        const val MIN_COMPACT_CONTROL_WIDTH = 80
        const val MEMBER_NAME_OFFSET = 96
        const val MEMBER_CARD_BACKGROUND = 0xA0202838.toInt()

        /** [resolvePlayerPreview] 為找不到 player-list profile 的玩家組出佔位 [GameProfile] 時使用的名稱。 */
        const val OFFLINE_PLAYER_PROFILE_NAME = "Offline"

        /** AI 策略按鈕相對卡片頂端（[RoomMemberGridLayout.CARD_TOP]）的垂直偏移。 */
        const val AI_STRATEGY_BUTTON_OFFSET_Y = 126

        /** 踢出按鈕相對卡片頂端（[RoomMemberGridLayout.CARD_TOP]）的垂直偏移。 */
        const val KICK_BUTTON_OFFSET_Y = 4
        val WIND_ORDER = mapOf(Wind.EAST to 0, Wind.SOUTH to 1, Wind.WEST to 2, Wind.NORTH to 3)
    }
}
