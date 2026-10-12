package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue.RuleCatalogueScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigScreen
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongHudLayoutEditorScreen
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.CycleButtonInput
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
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
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
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyDto
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
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 空桌、等待房間與進行中對局共用的桌級畫面。
 *
 * @property stateStore 目前客戶端持有的房間、對局與牌桌快照。
 * @property tableId 本畫面對應的牌桌識別碼。
 * @property configPresentations 規則設定的呈現定義登錄表。
 * @property configResolver 將設定解析成欄位定義與值的解析器。
 * @property ruleNames 規則名稱登錄表。
 * @property portraitRenderer 成員頭像繪製器。
 * @property aiStrategies 可選 AI 策略登錄表。
 * @property aiStrategyNames AI 策略名稱登錄表。
 * @property json 傳送房間操作使用的 JSON 格式。
 * @property networkRegistries 設定與網路 DTO 的轉換登錄表。
 * @property profileResolver 玩家名稱與外觀資料解析器。
 * @property ruleCatalogue 開啟規則一覽的控制器。
 * @param appearanceSources 成員外觀來源登錄表。
 * @param indicatorTextResolver 規則公開指標的文字解析器。
 * @param openSettings 是否直接進入設定頁。
 */
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
    private val ruleCatalogue: RuleCatalogueScreenController,
    openSettings: Boolean = false,
) : Screen(Text.translatable(MinecraftRoomScreenKeys.TITLE)) {
    /** 外觀解析與呈現降級的診斷記錄。 */
    private val logger = mahjongCraftLogger(RoomScreen::class)

    /** 目前顯示的房間或設定頁。 */
    private var page = if (openSettings) Page.SETTINGS else Page.ROOM

    /** 設定頁選中的分類；尚未選定時為 null。 */
    private var selectedCategoryId: String? = null

    /** 設定欄位清單的捲動與拖曳狀態。 */
    private val fieldScroll = ScrollState()

    /** 尚未送出或等待權威確認的設定草稿。 */
    private val draft = RoomSettingsDraft()

    /** 對局成員卡片內資訊列共用的捲動狀態。 */
    private val playingInfoScroll = ScrollState()

    /** 目前正在拖曳哪一列（grid row）的對局資訊 scrollbar；兩列共用同一個捲動位置，但幾何各自獨立。 */
    private var playingInfoDragRow = 0

    /** 等待室玩家卡片 grid 換列時的垂直捲動狀態，避免超出範圍的列疊在底部操作列上。 */
    private val memberScroll = ScrollState()

    /** 目前設定頁的套用按鈕；其他頁面為 null。 */
    private var applyButton: ButtonWidget? = null

    /** 目前設定頁的復原按鈕。 */
    private var undoButton: ButtonWidget? = null

    /** 目前設定頁的預設值重設按鈕。 */
    private var resetButton: ButtonWidget? = null

    /** 目前設定頁的完成按鈕。 */
    private var doneButton: ButtonWidget? = null

    /** 等目前輸入事件結束後再重建控制項的旗標。 */
    private var rebuildRequested = false

    /** 上次處理過的房間快照，用於偵測權威更新。 */
    private var lastRoomSnapshot = stateStore.roomSnapshot(tableId)

    /** 上次處理過的牌桌占用狀態。 */
    private var lastTable = stateStore.tableOccupancy(tableId)

    /**
     * 上一次看到房間資料時，自己是不是房間成員。
     *
     * 開局的瞬間房間資料會被對局資料取代，當下已經查不到自己是不是成員，因此在每次收到房間資料時記下來。
     * 記錄的是「當時」的身分，不是「曾經加入過」——加入後又離開的玩家，開局時不該被關閉畫面。
     */
    private var wasRoomMember = stateStore.tableOccupancy(tableId)?.occupancy == TableOccupancyDto.ROOM && stateStore.roomSnapshot(tableId)?.isInRoom == true

    /** 玩家模型預覽快取；未取得外觀資料時也可保存佔位模型。 */
    private val profilePreviews = mutableMapOf<Uuid, OtherClientPlayerEntity>()

    /** 決定各成員的模型或頭像呈現方式。 */
    private val appearanceResolver = RoomMemberAppearanceResolver(appearanceSources)

    /** 組成成員自風、分數與公開指標文字。 */
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

    /**
     * 建立頁籤按鈕；目前所在頁籤不可再次點擊。
     *
     * @param x 按鈕左界。
     * @param key 頁籤名稱翻譯鍵。
     * @param target 點擊後要顯示的頁面。
     * @return 切換頁面並要求重建控制項的按鈕。
     */
    private fun tabButton(x: Int, key: String, target: Page): ButtonWidget = RestartableMarqueeButtonWidget.builder(Text.translatable(key)) {
        page = target
        rebuild()
    }.dimensions(x, 24, 100, 20).build().also { it.active = page != target }

    /** 依牌桌占用狀態與本地成員身分建立房間頁的操作按鈕。 */
    private fun initRoomPage() {
        val table = stateStore.tableOccupancy(tableId) ?: return
        val room = stateStore.roomSnapshot(tableId)
        val bottom = height - 30
        when (table.occupancy) {
            TableOccupancyDto.VACANT -> addCenteredActions(
                listOf(ActionButton(MinecraftRoomScreenKeys.CREATE, RoomActionDto.Create(table.tableId), true)),
                bottom,
            )
            TableOccupancyDto.GAME -> addCenteredActions(emptyList(), bottom)
            TableOccupancyDto.ROOM -> {
                if (room == null) return
                val actions = mutableListOf<ActionButton>()

                /**
                 * 將操作加入本次房間頁的按鈕清單。
                 *
                 * @param key 按鈕文字翻譯鍵。
                 * @param action 點擊後傳送的房間操作。
                 * @param active 按鈕是否可點擊。
                 * @return 清單加入結果。
                 */
                fun button(key: String, action: RoomActionDto, active: Boolean = true) = actions.add(ActionButton(key, action, active))
                if (!room.isInRoom) {
                    button(MinecraftRoomScreenKeys.JOIN, RoomActionDto.Join(table.tableId), room.playerIds.size < room.gameConfig.ruleConfig.maxPlayers)
                } else if (room.isHost) {
                    button(MinecraftRoomScreenKeys.ADD_AI, RoomActionDto.AddAi(table.tableId), room.playerIds.size < room.gameConfig.ruleConfig.maxPlayers)
                    button(MinecraftRoomScreenKeys.START, RoomActionDto.Start(table.tableId), room.canStart)
                    button(MinecraftRoomScreenKeys.DISBAND, RoomActionDto.Disband(table.tableId))
                } else {
                    val selfId = client?.player?.uuid?.let(UUID::toString)
                    val ready = room.readyPlayerIds.any { it.toString() == selfId }
                    button(
                        if (ready) MinecraftRoomScreenKeys.CANCEL_READY else MinecraftRoomScreenKeys.READY,
                        RoomActionDto.ToggleReady(table.tableId),
                    )
                    button(MinecraftRoomScreenKeys.LEAVE, RoomActionDto.Leave(table.tableId))
                }
                addMemberCardWidgets(table.tableId, room)
                addCenteredActions(actions, bottom)
            }
        }
    }

    /**
     * 在等待室的成員卡片上加入 AI 策略與踢除按鈕。
     *
     * AI 策略對所有觀看者都顯示，非房主取得的是不可點擊的版本——策略是房間的公開資訊，只有變更它需要
     * 房主身分。踢除按鈕只有房主才有。房主本人的卡片兩者都不加。
     *
     * @param tableIdText 傳送房間操作使用的牌桌識別碼。
     * @param room 當前房間快照。
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
                        val keys = aiStrategies.getAllStrategyKeys().toList()
                        if (keys.isNotEmpty()) {
                            val next = keys[CycleButtonInput.nextIndex(currentIndex = keys.indexOf(current), size = keys.size, step = CycleButtonInput.step())]
                            send(RoomActionDto.ChangeAiStrategy(tableIdText, targetId.toString(), next))
                        }
                    }.dimensions(cardX + 8, cardY + AI_STRATEGY_BUTTON_OFFSET_Y, grid.cardWidth - 20, 18).build().also {
                        it.active = room.isHost
                        it.tooltip = Tooltip.of(
                            CycleButtonInput.withHint(
                                tooltip = aiStrategyTooltip(current),
                                optionCount = aiStrategies.getAllStrategyKeys().size,
                                active = it.active,
                            ),
                        )
                    },
                )
            }
            if (!room.isHost) return@forEachIndexed
            addDrawableChild(
                RestartableMarqueeButtonWidget.builder(Text.literal("×")) {
                    send(RoomActionDto.Kick(tableIdText, targetId.toString()))
                }.dimensions(cardX + grid.cardWidth - 23, cardY + KICK_BUTTON_OFFSET_Y, 16, 16).build().also {
                    it.tooltip = Tooltip.of(Text.translatable(MinecraftRoomScreenKeys.KICK))
                },
            )
        }
    }

    /** 依目前草稿建立分類、欄位與底部操作列，非房主只提供唯讀控制項。 */
    private fun initSettingsPage() {
        val authoritative = currentConfig() ?: return
        val room = stateStore.roomSnapshot(tableId)
        draft.beginIfAbsent(authoritative)
        val config = draft.config ?: authoritative
        val resolved = configResolver.resolve(config)
        val moduleId = resolved.ruleModuleId
        val definition = resolved.definition ?: return
        val canEditRoom = stateStore.tableOccupancy(tableId)?.occupancy == TableOccupancyDto.ROOM && room?.isHost == true
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
                it.active = draft.canDone(roomPlayerCount())
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

    /**
     * 依欄位編輯器規格建立布林、單選或整數控制項。
     *
     * @param field 欄位定義。
     * @param config 用於讀取目前值與提示內容的設定。
     * @param y 控制項頂端座標。
     * @param editable 是否允許修改此欄位。
     */
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
                        val index = CycleButtonInput.nextIndex(
                            currentIndex = editor.optionIds.indexOf(option),
                            size = editor.optionIds.size,
                            step = CycleButtonInput.step(),
                        )
                        updateDraft(field, GameConfigPresentationValue.ChoiceValue(editor.optionIds[index]))
                    }.dimensions(left, controlY, controlWidth, 20).build().withFieldTooltip(field, config, editable, editor.optionIds.size),
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

    /**
     * 計算窄視窗控制項的單欄水平範圍。
     *
     * @return 控制項左界與寬度；寬度至少為設定的最小值。
     */
    private fun compactControlBounds(): Pair<Int, Int> {
        val left = COMPACT_CONTENT_MARGIN
        val controlWidth = (width - COMPACT_CONTENT_MARGIN - COMPACT_SCROLLBAR_RESERVE).coerceAtLeast(MIN_COMPACT_CONTROL_WIDTH)
        return left to controlWidth
    }

    /**
     * 整數欄位三個控制項的水平範圍。
     *
     * @property minusX 減號按鈕左界。
     * @property textX 輸入框左界。
     * @property textWidth 輸入框寬度。
     * @property plusX 加號按鈕左界。
     */
    private data class IntegerControlBounds(val minusX: Int, val textX: Int, val textWidth: Int, val plusX: Int)

    /**
     * 設定欄位按鈕的啟用狀態與含操作提示的 tooltip。
     *
     * @param field 欄位定義。
     * @param config 讀取目前值使用的設定。
     * @param active 是否可操作。
     * @param optionCount 循環選項數；零表示不附加切換提示。
     * @return 原按鈕本身。
     */
    private fun ButtonWidget.withFieldTooltip(
        field: GameConfigFieldDefinition,
        config: GameConfig,
        active: Boolean,
        optionCount: Int = 0,
    ): ButtonWidget = apply {
        this.active = active
        tooltip = Tooltip.of(CycleButtonInput.withHint(tooltip = fieldTooltip(field, config), optionCount = optionCount, active = active))
    }

    /**
     * 更新草稿欄位並要求重建控制項。
     *
     * @param field 要更新的欄位。
     * @param value 新欄位值。
     */
    private fun updateDraft(field: GameConfigFieldDefinition, value: GameConfigPresentationValue) {
        draft.updateField(field, value)
        rebuild()
    }

    /**
     * 直接輸入整數時保留焦點，只更新草稿、驗證狀態與底部按鈕。
     *
     * @param field 要更新的欄位。
     * @param editor 整數輸入範圍與可空規格。
     * @param raw 原始輸入文字。
     */
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

    /**
     * 開啟規則一覽的「?」按鈕，位於規則切換鈕右側；依點擊當下設定頁顯示的規則與設定開啟，含尚未套用的修改。
     *
     * @param x 按鈕左界。
     */
    private fun addRuleCatalogueButton(x: Int) {
        addDrawableChild(
            ButtonWidget.builder(Text.literal(RULE_CATALOGUE_BUTTON_LABEL)) {
                val authoritative = currentConfig() ?: return@builder
                val displayed = draft.config ?: authoritative
                val context = roomRuleCatalogueContext(
                    ruleModuleId = configResolver.resolve(displayed).ruleModuleId,
                    displayed = displayed,
                    authoritative = authoritative,
                )
                client?.setScreen(ruleCatalogue.createScreen(context = context, exit = RoomRuleCatalogueExit(this)))
            }.dimensions(x, RULE_SELECTOR_Y, RULE_CATALOGUE_BUTTON_SIZE, RULE_CATALOGUE_BUTTON_SIZE).build().also {
                it.tooltip = Tooltip.of(
                    Text.empty()
                        .append(Text.translatable(MinecraftRoomScreenKeys.RULE_CATALOGUE_BUTTON).formatted(Formatting.GOLD))
                        .append("\n")
                        .append(Text.translatable(MinecraftRoomScreenKeys.RULE_CATALOGUE_BUTTON_DESCRIPTION).formatted(Formatting.GRAY)),
                )
            },
        )
    }

    /**
     * 建立規則切換鈕與規則一覽入口，窄視窗時兩者合計佔滿內容寬度。
     *
     * @param config 切換規則時保留其流程設定的目前設定。
     * @param moduleId 目前規則模組識別碼。
     * @param canEdit 是否有編輯房間的權限。
     * @param compact 是否採用窄視窗版面。
     */
    private fun addRuleSelector(config: GameConfig, moduleId: String, canEdit: Boolean, compact: Boolean) {
        val candidates = configPresentations.ruleModuleIds.sorted()
        val currentName = ruleName(moduleId)
        val (x, rowWidth) = if (compact) COMPACT_CONTENT_MARGIN to (width - COMPACT_CONTENT_MARGIN * 2) else 18 to 112
        val buttonWidth = rowWidth - RULE_CATALOGUE_BUTTON_SIZE - RULE_CATALOGUE_BUTTON_GAP
        addRuleCatalogueButton(x = x + buttonWidth + RULE_CATALOGUE_BUTTON_GAP)
        addDrawableChild(
            RestartableMarqueeButtonWidget.builder(currentName) {
                val selectable = candidates.filter { configPresentations.find(it)?.selectable == true }
                if (selectable.isEmpty()) return@builder
                val nextId = selectable[CycleButtonInput.nextIndex(currentIndex = selectable.indexOf(moduleId), size = selectable.size, step = CycleButtonInput.step())]
                val next = configPresentations.find(nextId) ?: return@builder
                draft.resetTo(config.copy(ruleConfig = next.defaultRuleConfig()))
                selectedCategoryId = null
                fieldScroll.reset()
                rebuild()
            }.dimensions(x, RULE_SELECTOR_Y, buttonWidth, 20).build().also { button ->
                button.active = canEdit && candidates.count { configPresentations.find(it)?.selectable == true } > 1
                button.tooltip = Tooltip.of(
                    CycleButtonInput.withHint(
                        tooltip = roomRuleSelectorTooltip(
                            currentModuleId = moduleId,
                            candidates = candidates.mapNotNull(configPresentations::find),
                            ruleName = ::ruleName,
                        ),
                        optionCount = candidates.count { configPresentations.find(it)?.selectable == true },
                        active = button.active,
                    ),
                )
            },
        )
    }

    /**
     * 建立包含說明、目前值、選項或數值限制的條列提示。
     *
     * @param field 欄位呈現定義。
     * @param config 讀取目前值及相依條件使用的設定。
     * @return 完整的多行欄位提示文字。
     */
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

    /**
     * 將欄位值轉成顯示文字。
     *
     * @param value 要呈現的欄位值。
     * @return 值對應的文字。
     */
    private fun presentationText(value: GameConfigPresentationValue): Text = gameConfigPresentationText(value)

    /**
     * 取得規則名稱，未登記時顯示原始識別碼。
     *
     * @param moduleId 規則模組識別碼。
     * @return 本地化名稱或識別碼。
     */
    private fun ruleName(moduleId: String): Text = ruleNames.find(moduleId)?.let(Text::translatable) ?: Text.literal(moduleId)

    /**
     * 將房間操作與關閉按鈕整組置中。
     *
     * @param actions 依顯示順序排列的房間操作。
     * @param y 按鈕列頂端座標。
     */
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

    /**
     * 建立目前策略與全部可選策略的提示。
     *
     * @param current 目前策略識別碼；未知時為 null。
     * @return 將目前策略標色的多行提示。
     */
    private fun aiStrategyTooltip(current: String?): Text = Text.empty()
        .append(Text.translatable(MinecraftRoomScreenKeys.AI_STRATEGY_TITLE).formatted(Formatting.GOLD))
        .append("\n• ")
        .append(Text.translatable(MinecraftRoomScreenKeys.CURRENT_VALUE, aiStrategyText(current)).formatted(Formatting.GREEN))
        .append("\n")
        .append(Text.translatable(MinecraftRoomScreenKeys.AVAILABLE_OPTIONS).formatted(Formatting.GOLD))
        .also { tooltip ->
            aiStrategies.getAllStrategyKeys().forEach { key ->
                tooltip.append("\n• ").append(aiStrategyText(key).copy().formatted(if (key == current) Formatting.GREEN else Formatting.WHITE))
            }
        }

    /** 草稿未過期且驗證通過時送出設定更新；不自行判定伺服器是否接受。 */
    private fun applyDraft() {
        if (!draft.canDone(roomPlayerCount())) return
        val table = stateStore.tableOccupancy(tableId) ?: return
        val config = draft.config ?: return
        MahjongChannels.roomAction.sendToServer(json, RoomActionDto.UpdateConfig(table.tableId, config.toDto(networkRegistries)))
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

    /**
     * 將草稿恢復成權威設定並要求重建。
     *
     * @param authoritative 要採用的權威設定；未知時不處理。
     */
    private fun restoreAuthoritativeDraft(authoritative: GameConfig? = currentConfig()) {
        val resolvedAuthoritative = authoritative ?: return
        draft.restoreAuthoritative(resolvedAuthoritative)
        rebuild()
    }

    /**
     * 依草稿、權威值與預設值同步底部按鈕及差異提示。
     *
     * @param current 目前草稿。
     * @param authoritative 目前權威設定。
     * @param defaults 用於判斷是否可重設的預設設定。
     */
    private fun refreshDraftButtons(
        current: GameConfig? = draft.config,
        authoritative: GameConfig? = currentConfig(),
        defaults: GameConfig? = current?.let { config ->
            configResolver.resolve(config).definition?.let { GameConfig(it.defaultRuleConfig()) }
        },
    ) {
        val roomPlayerCount = roomPlayerCount()
        applyButton?.active = draft.canApply(authoritative, roomPlayerCount)
        undoButton?.active = draft.canUndo(authoritative)
        resetButton?.active = draft.canReset(defaults)
        doneButton?.active = draft.canDone(roomPlayerCount)
        val changes = if (current != null && authoritative != null && current != authoritative && !draft.isStale) {
            Tooltip.of(gameConfigDifferenceText(configResolver, ruleNames, authoritative, current))
        } else {
            null
        }
        val playerLimit = current?.takeIf { draft.exceedsPlayerLimit(roomPlayerCount) }?.let { config ->
            Tooltip.of(playerLimitExceededText(config, roomPlayerCount).copy().formatted(Formatting.RED))
        }
        applyButton?.tooltip = playerLimit ?: changes
        doneButton?.tooltip = playerLimit
        undoButton?.tooltip = changes ?: Tooltip.of(undoTooltip())
    }

    /** 房間目前人數（含 AI）；房間資料尚未同步時為 0。 */
    private fun roomPlayerCount(): Int = stateStore.roomSnapshot(tableId)?.playerIds?.size ?: 0

    /**
     * 建立房間人數超過規則上限的說明。
     *
     * @param config 用於取得規則名稱與人數上限的設定。
     * @param roomPlayerCount 目前房間人數。
     * @return 超過人數上限的提示文字。
     */
    private fun playerLimitExceededText(config: GameConfig, roomPlayerCount: Int): Text = Text.translatable(
        MinecraftRoomScreenKeys.PLAYER_LIMIT_EXCEEDED,
        roomPlayerCount,
        ruleName(configResolver.resolve(config).ruleModuleId),
        config.ruleConfig.maxPlayers,
    )

    /** 建立復原按鈕的放棄草稿說明。 */
    private fun undoTooltip(): Text = Text.empty()
        .append(Text.translatable(MinecraftRoomScreenKeys.UNDO).formatted(Formatting.GOLD))
        .append("\n• ").append(Text.translatable(MinecraftRoomScreenKeys.UNDO_DISCARD).formatted(Formatting.WHITE))
        .append("\n• ").append(Text.translatable(MinecraftRoomScreenKeys.UNDO_RESTORE).formatted(Formatting.GRAY))

    /** 建立重設預設值按鈕的說明，提醒尚未儲存。 */
    private fun resetTooltip(): Text = Text.empty()
        .append(Text.translatable(MinecraftRoomScreenKeys.RESET_DEFAULTS_BUTTON).formatted(Formatting.GOLD))
        .append("\n• ").append(Text.translatable(MinecraftRoomScreenKeys.RESET_DEFAULTS).formatted(Formatting.WHITE))
        .append("\n• ").append(Text.translatable(MinecraftRoomScreenKeys.RESET_NOT_SAVED).formatted(Formatting.GRAY))

    /**
     * 建立並加入一個房間操作按鈕。
     *
     * @param x 按鈕左界。
     * @param y 按鈕頂端。
     * @param width 按鈕寬度。
     * @param key 按鈕名稱翻譯鍵。
     * @param action 點擊後傳送的房間操作。
     * @param active 是否可點擊。
     */
    private fun addActionButton(x: Int, y: Int, width: Int, key: String, action: RoomActionDto, active: Boolean = true) {
        addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(key)) {
                send(action)
            }.dimensions(x, y, width, 20).build().also { it.active = active },
        )
    }

    /**
     * 將房間操作傳送至伺服器。
     *
     * @param action 要傳送的操作。
     */
    private fun send(action: RoomActionDto) = MahjongChannels.roomAction.sendToServer(json, action)

    /**
     * 取得策略名稱；未知識別碼直接顯示，未提供識別碼時顯示 AI。
     *
     * @param strategyKey 策略識別碼。
     * @return 策略的顯示文字。
     */
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
        if (page == Page.ROOM && stateStore.tableOccupancy(tableId)?.occupancy == TableOccupancyDto.GAME && playingInfoScrollMaximum > 0) {
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
        if (stateStore.tableOccupancy(tableId) != lastTable || stateStore.roomSnapshot(tableId) != lastRoomSnapshot) {
            val previousTable = lastTable
            val previousRoom = lastRoomSnapshot
            val currentTable = stateStore.tableOccupancy(tableId)
            if (previousTable?.occupancy == TableOccupancyDto.ROOM && previousRoom != null) {
                wasRoomMember = previousRoom.isInRoom
            }
            if (
                previousTable?.occupancy == TableOccupancyDto.ROOM &&
                currentTable?.occupancy == TableOccupancyDto.GAME &&
                wasRoomMember
            ) {
                closeEntireScreen()
                return
            }
            lastTable = stateStore.tableOccupancy(tableId)
            lastRoomSnapshot = stateStore.roomSnapshot(tableId)
            if (draft.isReturningToRoomAfterApply && currentConfig() == draft.config) {
                draft.adoptAuthoritative(currentConfig())
                page = Page.ROOM
            }
            if (stateStore.tableOccupancy(tableId)?.occupancy == TableOccupancyDto.VACANT) {
                wasRoomMember = false
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

    /**
     * 這張桌子目前是否仍可使用：玩家在同一個維度、距離夠近，而且桌子本身還在。
     *
     * 桌子可能在畫面開著時被其他玩家破壞，因此距離之外還要確認方塊實體仍然存在；距離條件已經保證所在
     * 區塊是載入的，查不到方塊實體就代表桌子真的沒了。payload 沒有帶座標時無從判斷，一律視為仍可使用。
     *
     * @return 牌桌資料存在且可用時為 true；缺少位置資訊時也視為可用。
     */
    internal fun isTableStillAvailable(): Boolean {
        val table = stateStore.tableOccupancy(tableId) ?: return false
        val x = table.tableX ?: return true
        val y = table.tableY ?: return true
        val z = table.tableZ ?: return true
        val expectedDimension = table.dimensionId ?: return true
        val player = client?.player ?: return false
        val world = client?.world ?: return false
        if (world.registryKey.value.toString() != expectedDimension) return false
        if (player.squaredDistanceTo(x + 0.5, y + 0.5, z + 0.5) > 64.0) return false
        return world.getBlockEntity(BlockPos(x, y, z)) is MahjongTableBlockEntity
    }

    /**
     * 依牌桌狀態繪製空桌說明、等待成員或對局成員。
     *
     * @param context 畫面繪製情境。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     */
    private fun renderRoom(context: DrawContext, mouseX: Int, mouseY: Int) {
        val table = stateStore.tableOccupancy(tableId) ?: return
        when (table.occupancy) {
            TableOccupancyDto.VACANT -> context.drawCenteredTextWithShadow(textRenderer, Text.translatable(MinecraftRoomScreenKeys.EMPTY), width / 2, 70, 0xFFFFFF)
            TableOccupancyDto.GAME -> {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable(MinecraftRoomScreenKeys.PLAYING), width / 2, 50, 0xFFCC55)
                renderPlayingMembers(context, resolvePlayingPlayerInfo(), mouseX, mouseY)
            }
            TableOccupancyDto.ROOM -> stateStore.roomSnapshot(tableId)?.let { room ->
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
     * 顯示的是 player info（[resolvePlayingPlayerInfo]）而不是加入狀態，且該區塊內容過長時另外用
     * [playingInfoScroll] 捲動。
     *
     * @param context 畫面繪製情境。
     * @param players 對局玩家的公開資訊。
     * @param mouseX 游標水平座標，用於模型預覽朝向。
     * @param mouseY 游標垂直座標，用於模型預覽朝向。
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
        return roomMemberEntriesFrom(snapshot.players, stateStore.gameAiPlayerIds(tableId), ::resolveLocalPlayerName)
    }

    /**
     * 在牌桌周圍搜尋屬於此牌桌的玩家資訊實體。
     *
     * @return 第一個相符實體；世界、位置或實體尚未可用時為 null。
     */
    private fun resolvePlayerInfoEntity(): MahjongPlayerInfoEntity? {
        val table = stateStore.tableOccupancy(tableId) ?: return null
        val world = client?.world ?: return null
        val x = table.tableX ?: return null
        val y = table.tableY ?: return null
        val z = table.tableZ ?: return null
        return world.getEntitiesByClass(
            MahjongPlayerInfoEntity::class.java,
            Box(x - 4.0, y - 4.0, z - 4.0, x + 5.0, y + 5.0, z + 5.0),
        ) { it.managedTableId == tableId }.firstOrNull()
    }

    /**
     * 依輸入順序繪製等待室成員卡片與準備狀態。
     *
     * @param context 畫面繪製情境。
     * @param playerIds 依顯示順序排列的成員識別碼。
     * @param aiIds 由 AI 操控的成員識別碼。
     * @param memberStatus 要顯示的房主與準備狀態。
     * @param mouseX 游標水平座標，用於模型預覽朝向。
     * @param mouseY 游標垂直座標，用於模型預覽朝向。
     */
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

    /**
     * 繪製成員模型或降級頭像，並記錄解析器本次提供的診斷警告。
     *
     * @param context 畫面繪製情境。
     * @param playerId 成員識別碼。
     * @param ai 是否由 AI 操控。
     * @param x 卡片左界。
     * @param y 卡片頂端。
     * @param cardWidth 卡片寬度。
     * @param mouseX 游標水平座標，用於模型朝向。
     * @param mouseY 游標垂直座標，用於模型朝向。
     */
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
                logger.warn("Failed to resolve room member appearance provider {}", warning.providerId, warning.cause)
            is RoomMemberAppearanceResolver.Warning.MissingActorPreviewFactory ->
                logger.warn("No room actor preview factory is registered for {}; using portrait fallback", warning.actorKey)
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

    /**
     * 只在卡片列超出可見範圍時繪製捲軸。
     *
     * @param context 畫面繪製情境。
     * @param grid 成員卡片版面幾何。
     */
    private fun renderMemberGridScrollbar(context: DrawContext, grid: RoomMemberGridLayout) {
        if (grid.rows <= grid.visibleRows) return
        val column = grid.scrollbarColumn
        val layout = grid.scrollbar
        context.fill(column.trackLeft, layout.trackTop, column.trackRight, layout.trackBottom, 0x80505050.toInt())
        context.fill(column.trackLeft, layout.thumbTop, column.trackRight, layout.thumbTop + layout.thumbHeight, 0xFFD0D0D0.toInt())
    }

    /**
     * 為每個可見成員列繪製資訊捲軸，所有列共用資訊捲動位置。
     *
     * @param context 畫面繪製情境。
     * @param grid 成員卡片版面幾何。
     * @param totalInfoRows 完整資訊清單的最大行數。
     */
    private fun renderPlayingInfoScrollbar(context: DrawContext, grid: RoomMemberGridLayout, totalInfoRows: Int) {
        if (grid.maximumInfoScroll(totalInfoRows) == 0) return
        val column = grid.scrollbarColumn
        grid.visibleGridRows.forEach { row ->
            val layout = grid.infoScrollbar(row, totalInfoRows, playingInfoScroll.index)
            context.fill(column.trackLeft, layout.trackTop, column.trackRight, layout.trackBottom, 0x80505050.toInt())
            context.fill(column.trackLeft, layout.thumbTop, column.trackRight, layout.thumbTop + layout.thumbHeight, 0xFFD0D0D0.toInt())
        }
    }

    /**
     * 在卡片上方置中繪製成員頭像。
     *
     * @param context 畫面繪製情境。
     * @param playerId 成員識別碼。
     * @param isAi 是否使用 AI 外觀。
     * @param x 卡片左界。
     * @param y 卡片頂端。
     * @param cardWidth 卡片寬度，用於計算頭像大小與位置。
     */
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

    /**
     * 繪製設定欄位標籤、捲軸與驗證狀態。
     *
     * @param context 畫面繪製情境。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 被截斷且正被游標指向的標籤原文；沒有時為 null。
     */
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
            draft.exceedsPlayerLimit(roomPlayerCount()) -> playerLimitExceededText(config, roomPlayerCount()) to 0xFF5555
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

    /** 取得房間設定，沒有房間快照時改用進行中對局的設定；兩者皆無時為 null。 */
    private fun currentConfig(): GameConfig? = stateStore.roomSnapshot(tableId)?.gameConfig
        ?: stateStore.tableOccupancy(tableId)?.playingGameConfig?.let { dto -> dto.toDomain(networkRegistries) }

    /**
     * 取得成員名稱，AI 使用指定順序的編號名稱，真人使用本地名稱解析。
     *
     * @param playerId 成員識別碼。
     * @param ai 是否由 AI 操控。
     * @param orderedAiPlayerIds 用於分配 AI 顯示編號的順序。
     * @return 名稱或尚未解析完成的替代文字。
     */
    private fun memberName(playerId: Uuid, ai: Boolean, orderedAiPlayerIds: List<Uuid>): Text {
        if (ai) return Text.literal(aiPlayerDisplayName(playerId, orderedAiPlayerIds))
        return resolvedPlayerNameText(resolveLocalPlayerName(playerId))
    }

    /**
     * 優先使用玩家列表或已解析的名稱；未知時要求解析器載入，依解析器的帳號政策決定是否外部查詢。
     *
     * @param playerId 真人玩家識別碼。
     * @return 已知名稱；載入中或無法解析時為 null。
     */
    private fun resolveLocalPlayerName(playerId: Uuid): String? {
        val name = client?.networkHandler?.getPlayerListEntry(playerId.toJavaUuid())?.profile?.name
            ?: profileResolver.resolvedProfile(playerId)?.name
        if (name == null) profileResolver.requestResolve(playerId)
        return name
    }

    /**
     * 依目前視窗尺寸與捲動位置建立成員卡片版面。
     *
     * @param playerCount 要排列的玩家數。
     * @return 成員卡片版面幾何。
     */
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
    private fun currentMemberGrid(): RoomMemberGridLayout? = when (stateStore.tableOccupancy(tableId)?.occupancy) {
        TableOccupancyDto.ROOM -> stateStore.roomSnapshot(tableId)?.let { memberGrid(it.playerIds.size) }
        TableOccupancyDto.GAME -> playingGrid()
        else -> null
    }

    /**
     * 依目前視窗尺寸、草稿分類與捲動位置建立設定頁版面。
     *
     * @param fieldCount 目前分類的完整欄位數。
     * @return 設定頁版面幾何。
     */
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

    /**
     * 只在設定欄位超出可見範圍時繪製捲軸。
     *
     * @param context 畫面繪製情境。
     * @param fieldCount 目前分類的完整欄位數。
     */
    private fun renderScrollbar(context: DrawContext, fieldCount: Int) {
        val settings = settingsLayout(fieldCount)
        val scrollbar = settings.scrollbar
        if (scrollbar.maximumScroll <= 0) return
        val column = settings.scrollbarColumn
        context.fill(column.trackLeft, scrollbar.trackTop, column.trackRight, scrollbar.trackBottom, 0x80505050.toInt())
        context.fill(column.trackLeft, scrollbar.thumbTop, column.trackRight, scrollbar.thumbTop + scrollbar.thumbHeight, 0xFFD0D0D0.toInt())
    }

    /**
     * 依像素寬度截斷文字並補省略號；寬度不足以容納省略號時仍保留省略號。
     *
     * @param text 原始文字。
     * @param maximumWidth 可用像素寬度；非正值時回傳空文字。
     * @return 原文或去除原樣式後附省略號的文字。
     */
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
     * 優先使用世界中的玩家，否則以玩家列表或已解析 profile 建立純客戶端預覽。
     * 未取得 profile 時要求 [profileResolver] 載入並使用問號佔位模型；是否外部查詢由解析器的帳號政策決定。
     * 取得 profile 後，下次呼叫會取代佔位模型。
     *
     * @param playerId 真人玩家識別碼。
     * @return 可用的玩家或預覽模型；客戶端世界不可用時為 null。
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

    /**
     * 取得布林值的本地化文字。
     *
     * @param enabled 是否啟用。
     * @return 對應真或假的文字。
     */
    private fun booleanText(enabled: Boolean): Text = Text.translatable(
        if (enabled) MinecraftRoomScreenKeys.TRUE else MinecraftRoomScreenKeys.FALSE,
    )

    /**
     * 取得設定選項的本地化文字。
     *
     * @param optionId 選項識別碼。
     * @return 對應翻譯鍵的文字。
     */
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
    private fun hasUnsavedSettingsDraft(): Boolean = draft.canDone(roomPlayerCount()) && draft.hasUnsavedChanges(currentConfig())

    /** 關閉整個 RoomScreen，不套用設定頁的階層式 Esc 行為。 */
    private fun closeEntireScreen() {
        client?.setScreen(null)
    }

    /** 在目前輸入事件完成後安全重建 widgets，避免舊 widget 被重新設為 focus。 */
    private fun rebuild() {
        rebuildRequested = true
    }

    override fun shouldPause(): Boolean = false

    /** 桌級畫面目前顯示的頁面。 */
    private enum class Page {
        /** 空桌、等待室或進行中對局的成員頁。 */
        ROOM,

        /** 規則與流程設定頁。 */
        SETTINGS,
    }

    /**
     * 房間底部操作按鈕的建立資料。
     *
     * @property key 名稱翻譯鍵。
     * @property action 點擊後傳送的房間操作。
     * @property active 是否可點擊。
     */
    private data class ActionButton(
        val key: String,
        val action: RoomActionDto,
        val active: Boolean,
    )

    /** 成員卡片下方要呈現的房間狀態。 */
    private sealed interface MemberStatus {
        /**
         * 等待開局的成員狀態。
         *
         * @property readyPlayerIds 已準備完成的成員識別碼。
         * @property hostId 房主識別碼。
         */
        data class Waiting(
            val readyPlayerIds: Set<Uuid>,
            val hostId: Uuid,
        ) : MemberStatus

        /** 已進入對局，不顯示等待室準備狀態。 */
        data object Playing : MemberStatus
    }

    private companion object {
        /** 一般版面設定欄位標籤的左界。 */
        const val SETTINGS_FIELD_LABEL_X = 150

        /** 欄位標籤與控制項之間的水平間距。 */
        const val SETTINGS_FIELD_LABEL_GAP = 8

        /** 輸入文字置中計算使用的可視字高。 */
        const val VANILLA_VISIBLE_TEXT_HEIGHT = 8

        /** 窄視窗內容區的水平邊距。 */
        const val COMPACT_CONTENT_MARGIN = 18

        /** 規則切換鈕與規則一覽按鈕的上界。 */
        const val RULE_SELECTOR_Y = 54

        /** 規則一覽按鈕的邊長。 */
        const val RULE_CATALOGUE_BUTTON_SIZE = 20

        /** 規則切換鈕與規則一覽按鈕之間的距離。 */
        const val RULE_CATALOGUE_BUTTON_GAP = 4

        /** 規則一覽按鈕上的文字。 */
        const val RULE_CATALOGUE_BUTTON_LABEL = "?"

        /** 窄視窗整數欄位加減按鈕的寬度。 */
        const val COMPACT_INTEGER_BUTTON_WIDTH = 24

        /** 整數輸入框與加減按鈕之間的間距。 */
        const val COMPACT_INTEGER_BUTTON_GAP = 4

        /** 右側需保留給 scrollbar 的寬度，避免 compact 控制項覆蓋到它。 */
        const val COMPACT_SCROLLBAR_RESERVE = 20

        /** 窄視窗控制項的最小寬度。 */
        const val MIN_COMPACT_CONTROL_WIDTH = 80

        /** 成員名稱相對卡片頂端的垂直偏移。 */
        const val MEMBER_NAME_OFFSET = 96

        /** 成員卡片的 ARGB 背景顏色。 */
        const val MEMBER_CARD_BACKGROUND = 0xA0202838.toInt()

        /** [resolvePlayerPreview] 為找不到 player-list profile 的玩家組出佔位 [GameProfile] 時使用的名稱。 */
        const val OFFLINE_PLAYER_PROFILE_NAME = "Offline"

        /** AI 策略按鈕相對卡片頂端（[RoomMemberGridLayout.CARD_TOP]）的垂直偏移。 */
        const val AI_STRATEGY_BUTTON_OFFSET_Y = 126

        /** 踢出按鈕相對卡片頂端（[RoomMemberGridLayout.CARD_TOP]）的垂直偏移。 */
        const val KICK_BUTTON_OFFSET_Y = 4

        /** 對局成員卡片依東、南、西、北排列的自風順序。 */
        val WIND_ORDER = mapOf(Wind.EAST to 0, Wind.SOUTH to 1, Wind.WEST to 2, Wind.NORTH to 3)
    }
}
