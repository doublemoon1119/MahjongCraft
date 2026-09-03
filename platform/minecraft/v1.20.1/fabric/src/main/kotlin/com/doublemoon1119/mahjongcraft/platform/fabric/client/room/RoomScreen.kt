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
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.SettingsFooterLayout
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
    private var fieldScroll = 0
    private var draftConfig: GameConfig? = null
    private var authoritativeConfigAtDraftStart: GameConfig? = null
    private var draftStale = false
    private var validationFailed = false
    private val invalidFieldIds = mutableSetOf<String>()
    private var draggingScrollbar = false
    private var playingInfoScroll = 0
    private var draggingPlayingInfoScrollbar = false
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
                    val cardWidth = memberCardWidth(room.playerIds.size)
                    val total = room.playerIds.size * cardWidth
                    room.playerIds.forEachIndexed { index, targetId ->
                        if (targetId == room.hostId) return@forEachIndexed
                        val cardX = width / 2 - total / 2 + index * cardWidth
                        if (targetId in room.aiPlayerIds) {
                            val current = room.aiPlayerStrategyKeys[targetId]
                            addDrawableChild(
                                RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftRoomScreenKeys.AI_STRATEGY, aiStrategyText(current))) {
                                    val keys = aiStrategies.getAllStrategyKeys().sorted()
                                    if (keys.isNotEmpty()) {
                                        val next = keys[(keys.indexOf(current).coerceAtLeast(0) + 1) % keys.size]
                                        send(RoomScreenActionDto.ChangeAiStrategy(lobby.tableId, targetId.toString(), next))
                                    }
                                }.dimensions(cardX + 8, 184, cardWidth - 20, 18).build().also {
                                    it.tooltip = Tooltip.of(aiStrategyTooltip(current))
                                },
                            )
                        }
                        addDrawableChild(
                            RestartableMarqueeButtonWidget.builder(Text.literal("×")) {
                                send(RoomScreenActionDto.Kick(lobby.tableId, targetId.toString()))
                            }.dimensions(cardX + cardWidth - 23, 62, 16, 16).build().also {
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
        addRuleSelector(config, moduleId, canEditRoom)
        var categoryY = 82
        categories.forEach { category ->
            addDrawableChild(
                RestartableMarqueeButtonWidget.builder(Text.translatable(category.nameTranslationKey)) {
                    selectedCategoryId = category.id
                    fieldScroll = 0
                    rebuild()
                }.dimensions(18, categoryY, 112, 20).build().also { it.active = selectedCategoryId != category.id },
            )
            categoryY += 24
        }
        val categoryFields = definition.fields.filter { it.categoryId == selectedCategoryId }
        val maximumVisibleFields = maximumVisibleFields()
        fieldScroll = fieldScroll.coerceIn(0, (categoryFields.size - maximumVisibleFields).coerceAtLeast(0))
        var fieldY = SETTINGS_FIELDS_TOP
        categoryFields.drop(fieldScroll).take(maximumVisibleFields).forEach { field ->
            addFieldControls(field, config, 150, fieldY, editable && field.isEditable && field.isEnabled(config))
            fieldY += 28
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
            }.dimensions(footer.undoX, height - 30, footer.actionWidth, 20).build().also {
                it.tooltip = Tooltip.of(undoTooltip())
            }
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

    private fun addFieldControls(field: GameConfigFieldDefinition, config: GameConfig, x: Int, y: Int, editable: Boolean) {
        val value = field.read(config)
        val controlY = y
        when (val editor = field.editor) {
            GameConfigEditorSpec.BooleanToggle -> {
                val enabled = (value as GameConfigPresentationValue.BooleanValue).enabled
                addDrawableChild(
                    RestartableMarqueeButtonWidget.builder(booleanText(enabled)) {
                        updateDraft(field, GameConfigPresentationValue.BooleanValue(!enabled))
                    }.dimensions(width - 152, controlY, 124, 20).build().withFieldTooltip(field, config, editable),
                )
            }
            is GameConfigEditorSpec.SingleChoice -> {
                val option = (value as GameConfigPresentationValue.ChoiceValue).optionId
                addDrawableChild(
                    RestartableMarqueeButtonWidget.builder(optionText(option)) {
                        val index = editor.optionIds.indexOf(option).coerceAtLeast(0)
                        updateDraft(field, GameConfigPresentationValue.ChoiceValue(editor.optionIds[(index + 1) % editor.optionIds.size]))
                    }.dimensions(width - 212, controlY, 184, 20).build().withFieldTooltip(field, config, editable),
                )
            }
            is GameConfigEditorSpec.IntegerInput -> {
                val number = (value as GameConfigPresentationValue.IntegerValue).number
                addDrawableChild(
                    RestartableMarqueeButtonWidget.builder(Text.literal("−")) {
                        val next = RoomConfigIntegerControl.decrement(
                            current = number,
                            editor = editor,
                            shiftHeld = hasShiftDown(),
                        )
                        updateDraft(field, GameConfigPresentationValue.IntegerValue(next))
                    }.dimensions(width - 212, controlY, 24, 20).build().withFieldTooltip(
                        field,
                        config,
                        editable && RoomConfigIntegerControl.canDecrease(current = number, editor = editor),
                    ),
                )
                val input = CenteredIntegerTextFieldWidget(
                    textRenderer,
                    width - 184,
                    controlY,
                    128,
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
                    }.dimensions(width - 52, controlY, 24, 20).build().withFieldTooltip(
                        field,
                        config,
                        editable && RoomConfigIntegerControl.canIncrease(current = number, editor = editor),
                    ),
                )
            }
        }
    }

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

    /** 以單一切換按鈕顯示規則，並在 tooltip 條列所有已登記規則。 */
    private fun addRuleSelector(config: GameConfig, moduleId: String, canEdit: Boolean) {
        val candidates = configPresentations.ruleModuleIds.sorted()
        val currentName = ruleName(moduleId)
        addDrawableChild(
            RestartableMarqueeButtonWidget.builder(currentName) {
                val selectable = candidates.filter { configPresentations.find(it)?.selectable == true }
                if (selectable.isEmpty()) return@builder
                val nextId = selectable[(selectable.indexOf(moduleId).coerceAtLeast(0) + 1) % selectable.size]
                val next = configPresentations.find(nextId) ?: return@builder
                draftConfig = config.copy(ruleConfig = next.defaultRuleConfig())
                selectedCategoryId = null
                fieldScroll = 0
                invalidFieldIds.clear()
                validationFailed = false
                rebuild()
            }.dimensions(18, 54, 112, 20).build().also { button ->
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
            fieldScroll = (fieldScroll + if (amount < 0) 1 else -1).coerceAtLeast(0)
            rebuild()
            return true
        }
        if (page == Page.ROOM && stateStore.tableLobby?.phase == TableLobbyPhaseDto.PLAYING) {
            val maximumScroll = maximumPlayingInfoScroll()
            val next = (playingInfoScroll + if (amount < 0) 1 else -1).coerceIn(0, maximumScroll)
            if (next != playingInfoScroll) playingInfoScroll = next
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (page == Page.SETTINGS && button == 0 && isOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true
            updateScrollFromMouse(mouseY)
            return true
        }
        if (page == Page.ROOM && button == 0 && isOverPlayingInfoScrollbar(mouseX, mouseY)) {
            draggingPlayingInfoScrollbar = true
            updatePlayingInfoScrollFromMouse(mouseY)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (draggingScrollbar && button == 0) {
            updateScrollFromMouse(mouseY)
            return true
        }
        if (draggingPlayingInfoScrollbar && button == 0) {
            updatePlayingInfoScrollFromMouse(mouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        draggingScrollbar = false
        draggingPlayingInfoScrollbar = false
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
            rebuild()
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
            rebuild()
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
        val maximumScroll = (sortedPlayers.maxOfOrNull { playingInfoRows(it, dealerPlayerId).size } ?: 0)
            .minus(visibleRows).coerceAtLeast(0)
        playingInfoScroll = playingInfoScroll.coerceIn(0, maximumScroll)
        val cardWidth = memberCardWidth(sortedPlayers.size)
        val total = sortedPlayers.size * cardWidth
        sortedPlayers.forEachIndexed { index, player ->
            val x = width / 2 - total / 2 + index * cardWidth
            val y = MEMBER_CARD_TOP
            context.fill(x + 2, y, x + cardWidth - 4, playingCardBottom(), MEMBER_CARD_BACKGROUND)
            renderMemberAppearance(context, player.playerId, player.isAi, x, y, cardWidth, mouseX, mouseY)
            context.drawCenteredTextWithShadow(
                textRenderer,
                fitText(Text.literal(player.playerName), cardWidth - 12),
                x + cardWidth / 2,
                y + MEMBER_NAME_OFFSET,
                0xFFFFFF,
            )
            playingInfoRows(player, dealerPlayerId).drop(playingInfoScroll).take(visibleRows).forEachIndexed { rowIndex, row ->
                context.drawCenteredTextWithShadow(
                    textRenderer,
                    fitText(row.first, cardWidth - 12),
                    x + cardWidth / 2,
                    PLAYING_INFO_TOP + rowIndex * PLAYING_INFO_ROW_HEIGHT,
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
        val cardWidth = memberCardWidth(playerIds.size)
        val total = playerIds.size * cardWidth
        playerIds.forEachIndexed { index, playerId ->
            val x = width / 2 - total / 2 + index * cardWidth
            val y = MEMBER_CARD_TOP
            context.fill(x + 2, y, x + cardWidth - 4, y + 146, 0xA0202838.toInt())
            val ai = playerId in aiIds
            renderMemberAppearance(context, playerId, ai, x, y, cardWidth, mouseX, mouseY)
            context.drawCenteredTextWithShadow(
                textRenderer,
                fitText(memberName(playerId, ai, playerIds.filter(aiIds::contains)), cardWidth - 12),
                x + cardWidth / 2,
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
                context.drawCenteredTextWithShadow(textRenderer, it, x + cardWidth / 2, y + 111, if (highlighted) 0x88FF88 else 0xAAAAAA)
            }
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

    private fun playingCardBottom(): Int = (height - 38).coerceAtLeast(PLAYING_INFO_TOP + PLAYING_INFO_ROW_HEIGHT + 4)

    private fun visiblePlayingInfoRows(): Int = ((playingCardBottom() - PLAYING_INFO_TOP - 4) / PLAYING_INFO_ROW_HEIGHT).coerceAtLeast(1)

    private fun maximumPlayingInfoScroll(): Int {
        val dealerPlayerId = resolvePlayerInfoEntity()?.dealerPlayerId ?: stateStore.gameSnapshot?.dealerPlayerId
        return ((resolvePlayingPlayerInfo().maxOfOrNull { playingInfoRows(it, dealerPlayerId).size } ?: 0) - visiblePlayingInfoRows())
            .coerceAtLeast(0)
    }

    private fun renderPlayingInfoScrollbar(context: DrawContext, maximumScroll: Int) {
        if (maximumScroll == 0) return
        val trackHeight = playingCardBottom() - PLAYING_INFO_TOP
        val totalRows = maximumScroll + visiblePlayingInfoRows()
        val thumbHeight = (trackHeight * visiblePlayingInfoRows() / totalRows).coerceAtLeast(12)
        val thumbY = PLAYING_INFO_TOP + (trackHeight - thumbHeight) * playingInfoScroll / maximumScroll
        context.fill(width - 14, PLAYING_INFO_TOP, width - 9, playingCardBottom(), 0x80505050.toInt())
        context.fill(width - 14, thumbY, width - 9, thumbY + thumbHeight, 0xFFD0D0D0.toInt())
    }

    private fun isOverPlayingInfoScrollbar(mouseX: Double, mouseY: Double): Boolean = maximumPlayingInfoScroll() > 0 &&
        mouseX >= width - 18 &&
        mouseX <= width - 5 &&
        mouseY >= PLAYING_INFO_TOP &&
        mouseY <= playingCardBottom()

    private fun updatePlayingInfoScrollFromMouse(mouseY: Double) {
        val maximumScroll = maximumPlayingInfoScroll()
        if (maximumScroll == 0) return
        val fraction = ((mouseY - PLAYING_INFO_TOP) / (playingCardBottom() - PLAYING_INFO_TOP)).coerceIn(0.0, 1.0)
        playingInfoScroll = (fraction * maximumScroll).toInt()
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
        if (definition == null) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable(MinecraftRoomScreenKeys.READ_ONLY),
                width / 2,
                SETTINGS_FIELDS_TOP,
                0xAAAAAA,
            )
            return null
        }
        var hoveredLabel: Text? = null
        definition.fields.filter { it.categoryId == selectedCategoryId }
            .drop(fieldScroll)
            .take(maximumVisibleFields())
            .forEachIndexed { index, field ->
                val label = Text.translatable(field.nameTranslationKey)
                val labelY = SETTINGS_FIELDS_TOP + (20 - VANILLA_VISIBLE_TEXT_HEIGHT) / 2 + index * 28
                val controlLeft = fieldControlLeft(field)
                val maximumLabelWidth = (controlLeft - SETTINGS_FIELD_LABEL_X - SETTINGS_FIELD_LABEL_GAP).coerceAtLeast(1)
                val fittedLabel = fitText(label, maximumLabelWidth)
                context.drawTextWithShadow(textRenderer, fittedLabel, SETTINGS_FIELD_LABEL_X, labelY, 0xFFFFFF)
                if (
                    fittedLabel.string != label.string &&
                    mouseX in SETTINGS_FIELD_LABEL_X until controlLeft &&
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
            val contentWidth = (width - SETTINGS_FIELD_LABEL_X - 20).coerceAtLeast(1)
            context.drawCenteredTextWithShadow(
                textRenderer,
                fitText(message, contentWidth),
                SETTINGS_FIELD_LABEL_X + contentWidth / 2,
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

    private fun memberCardWidth(playerCount: Int): Int = minOf(126, (width - 16) / playerCount.coerceAtLeast(1))

    /** 保留底部操作列後目前視窗能容納的設定欄位數。 */
    private fun settingsContentBottom(): Int = height - SETTINGS_BOTTOM_RESERVED_HEIGHT

    private fun settingsStatusY(): Int = height - SETTINGS_STATUS_BOTTOM_OFFSET

    private fun maximumVisibleFields(): Int = ((settingsContentBottom() - SETTINGS_FIELDS_TOP) / 28).coerceAtLeast(1)

    /** 依 editor 實際控制元件的左界計算本地化標籤可用寬度。 */
    private fun fieldControlLeft(field: GameConfigFieldDefinition): Int = when (field.editor) {
        GameConfigEditorSpec.BooleanToggle -> width - 152
        is GameConfigEditorSpec.SingleChoice,
        is GameConfigEditorSpec.IntegerInput,
        -> width - 212
    }

    /** 只在內容超出可見範圍時繪製可拖曳 scrollbar。 */
    private fun renderScrollbar(context: DrawContext, fieldCount: Int) {
        val visible = maximumVisibleFields()
        if (fieldCount <= visible) return
        val trackTop = SETTINGS_FIELDS_TOP
        val trackBottom = settingsContentBottom()
        val trackHeight = trackBottom - trackTop
        val thumbHeight = (trackHeight * visible / fieldCount).coerceAtLeast(12)
        val maximumScroll = fieldCount - visible
        val thumbY = trackTop + (trackHeight - thumbHeight) * fieldScroll / maximumScroll
        context.fill(width - 14, trackTop, width - 9, trackBottom, 0x80505050.toInt())
        context.fill(width - 14, thumbY, width - 9, thumbY + thumbHeight, 0xFFD0D0D0.toInt())
    }

    private fun isOverScrollbar(mouseX: Double, mouseY: Double): Boolean = mouseX >= width - 18 &&
        mouseX <= width - 5 &&
        mouseY >= SETTINGS_FIELDS_TOP &&
        mouseY <= settingsContentBottom()

    private fun updateScrollFromMouse(mouseY: Double) {
        val definition = (draftConfig ?: currentConfig())?.let(configResolver::resolve)?.definition ?: return
        val fieldCount = definition.fields.count { it.categoryId == selectedCategoryId }
        val maximumScroll = (fieldCount - maximumVisibleFields()).coerceAtLeast(0)
        if (maximumScroll == 0) return
        val fraction = ((mouseY - SETTINGS_FIELDS_TOP) / (settingsContentBottom() - SETTINGS_FIELDS_TOP)).coerceIn(0.0, 1.0)
        val next = (fraction * maximumScroll).toInt()
        if (next != fieldScroll) {
            fieldScroll = next
            rebuild()
            draggingScrollbar = true
        }
    }

    /** 依實際像素寬度截斷成員名稱並補省略號。 */
    private fun fitText(text: Text, maximumWidth: Int): Text {
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

    /** Esc 在設定頁放棄未套用草稿並返回玩家頁；玩家頁才離開整個畫面。 */
    override fun close() {
        if (page == Page.SETTINGS) {
            restoreAuthoritativeDraft()
            page = Page.ROOM
            rebuild()
        } else {
            closeEntireScreen()
        }
    }

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
        const val MEMBER_CARD_TOP = 58
        const val MEMBER_NAME_OFFSET = 96
        const val MEMBER_CARD_BACKGROUND = 0xA0202838.toInt()
        const val PLAYING_INFO_TOP = 170
        const val PLAYING_INFO_ROW_HEIGHT = 12
        val WIND_ORDER = mapOf(Wind.EAST to 0, Wind.SOUTH to 1, Wind.WEST to 2, Wind.NORTH to 3)
    }
}
