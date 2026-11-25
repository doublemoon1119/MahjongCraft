package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableController
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowseContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterClientStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.catalogue.RuleCatalogueScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character.GuiEntityModelRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character.RoomFigureArea
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.fitClientText
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.CardBackground
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOption
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOverlay
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.MahjongScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.PixelIcon
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScrollState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.ClientPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.ClientPlayerProfileResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.playerInfoDisplayName
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.OfflinePlayerPreviewEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.PlayerPortraitRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.PublicPlayerIndicatorTextResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.table.ClientTableSpaceObstructionStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongPlayerInfoEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoEntry
import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 空桌、等待房間與進行中對局共用的桌級畫面。
 *
 * @property stateStore 目前客戶端持有的房間、對局與牌桌快照。
 * @property characters 此連線已授權的角色描述。
 * @property tableId 本畫面對應的牌桌識別碼。
 * @param configPresentations 規則設定的呈現定義登錄表。
 * @param configResolver 將設定解析成欄位定義與值的解析器。
 * @param ruleNames 規則名稱登錄表。
 * @property portraitRenderer 成員頭像繪製器。
 * @property aiStrategies 可選 AI 策略登錄表。
 * @property aiStrategyNames AI 策略名稱登錄表。
 * @param appearanceSources 成員外觀來源登錄表。
 * @param indicatorTextResolver 規則公開指標的文字解析器。
 * @property json 傳送房間操作使用的 JSON 格式。
 * @property networkRegistries 設定與網路 DTO 的轉換登錄表。
 * @property profileResolver 玩家名稱與外觀資料解析器。
 * @property displayNames 既有真人與角色名稱的共用解析器。
 * @property clock 共用的客戶端回覆等待時鐘。
 * @property ruleCatalogue 開啟規則一覽的控制器。
 * @property tableSpaceObstructions 等待開局桌子被擋住的擺牌格。
 * @param openSettings 是否直接進入設定頁。
 */
class RoomScreen(
    private val stateStore: ClientMahjongStateStore,
    private val characters: AiCharacterClientStateStore,
    val tableId: Uuid,
    configPresentations: GameConfigPresentationRegistry,
    configResolver: GameConfigPresentationResolver,
    ruleNames: RuleModuleDisplayNameRegistry,
    private val portraitRenderer: PlayerPortraitRenderer,
    private val aiStrategies: MahjongAiStrategyRegistry,
    private val aiStrategyNames: AiStrategyDisplayNameRegistry,
    appearanceSources: RoomMemberAppearanceSourceRegistry,
    indicatorTextResolver: PublicPlayerIndicatorTextResolver,
    private val json: NetworkJson,
    private val networkRegistries: NetworkDtoRegistries,
    private val profileResolver: ClientPlayerProfileResolver,
    private val displayNames: ClientPlayerDisplayNameResolver,
    private val clock: MonotonicClock,
    private val ruleCatalogue: RuleCatalogueScreenController,
    private val tableSpaceObstructions: ClientTableSpaceObstructionStore,
    openSettings: Boolean = false,
) : MahjongScreen(Component.translatable(MinecraftRoomScreenKeys.TITLE)) {
    /** 外觀解析與呈現降級的診斷記錄。 */
    private val logger = mahjongCraftLogger(RoomScreen::class)

    /** 上一次建立畫面時這張桌子是否被擋住，改變時重建開始按鈕。 */
    private var lastSpaceObstructed: Boolean = tableSpaceObstructions.isObstructed(tableId)

    /** 目前顯示的房間或設定頁。 */
    private var page = if (openSettings) Page.SETTINGS else Page.ROOM

    /** 兩個頁面共用的選擇器展開清單。 */
    private val dropdown = DropdownOverlay()

    /** 規則與流程設定頁。 */
    private val settingsPage = RoomSettingsPage(
        host = SettingsPageHost(),
        stateStore = stateStore,
        tableId = tableId,
        configPresentations = configPresentations,
        configResolver = configResolver,
        ruleNames = ruleNames,
        json = json,
        networkRegistries = networkRegistries,
    )

    /** 對局成員卡片內資訊清單的捲動狀態，每位玩家各自一份。 */
    private val playingInfoScrolls = mutableMapOf<Uuid, ScrollState>()

    /** 正在拖曳資訊捲軸的玩家；沒有拖曳時為 null。 */
    private var playingInfoDragPlayer: Uuid? = null

    /** 等待室玩家卡片 grid 換列時的垂直捲動狀態，避免超出範圍的列疊在底部操作列上。 */
    private val memberScroll = ScrollState()

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
    private val profilePreviews = mutableMapOf<Uuid, RemotePlayer>()

    /** 決定各成員的模型或頭像呈現方式。 */
    private val appearanceResolver = RoomMemberAppearanceResolver(appearanceSources, portraitRenderer::hasCharacterPreview)

    /** 組成成員自風、分數與公開指標文字。 */
    private val memberPresentation = RoomMemberPresentation(indicatorTextResolver)

    /** 判斷這個畫面是否正在顯示指定桌子，供桌子移除通知關閉過期畫面。 */
    internal fun isForTable(tableId: Uuid): Boolean = this.tableId == tableId

    override fun init() {
        dropdown.close()
        val pageSwitchLeft = width / 2 - FormScreenLayout.PAGE_SWITCH_WIDTH - FormScreenLayout.PAGE_SWITCH_GAP / 2
        addRenderableWidget(pageButton(pageSwitchLeft, MinecraftRoomScreenKeys.PAGE_ROOM, Page.ROOM))
        addRenderableWidget(
            pageButton(
                x = pageSwitchLeft + FormScreenLayout.PAGE_SWITCH_WIDTH + FormScreenLayout.PAGE_SWITCH_GAP,
                key = MinecraftRoomScreenKeys.PAGE_SETTINGS,
                target = Page.SETTINGS,
            ).also { it.active = currentConfig() != null },
        )
        val room = stateStore.roomSnapshot(tableId)
        if (room?.isHost == true && room.excludedPlayerIds.isNotEmpty()) {
            addRenderableWidget(
                RoomExcludedParticipantsButton(
                    x = width / 2 + FormScreenLayout.PAGE_SWITCH_WIDTH + FormScreenLayout.PAGE_SWITCH_GAP * 3 / 2,
                    y = FormScreenLayout.PAGE_SWITCH_Y,
                    count = { stateStore.roomSnapshot(tableId)?.excludedPlayerIds?.size ?: 0 },
                    open = {
                        minecraft?.setScreen(
                            RoomExcludedParticipantsScreen(
                                parent = this,
                                readRoom = { stateStore.roomSnapshot(tableId) },
                                name = { id -> Component.literal(displayNames.resolve(tableId, id.toString(), id in stateStore.roomSnapshot(tableId)?.excludedAiPlayerIds.orEmpty())) },
                                face = { context, id, x, y, size -> portraitRenderer.renderExcludedGui(tableId, id, id in stateStore.roomSnapshot(tableId)?.excludedAiPlayerIds.orEmpty(), context, x, y, size) },
                                allow = { id -> send(RoomActionDto.AllowParticipant(tableId.toString(), id.toString())) },
                                allowAll = { send(RoomActionDto.AllowAllParticipants(tableId.toString())) },
                                clock = clock,
                            ),
                        )
                    },
                ),
            )
        }
        when (page) {
            Page.ROOM -> initRoomPage()
            Page.SETTINGS -> settingsPage.init()
        }
    }

    /**
     * 建立頁面切換按鈕；目前所在的頁面以白色外框標示，再點一次沒有作用。
     *
     * @param x 按鈕左界。
     * @param key 頁面名稱翻譯鍵。
     * @param target 點擊後要顯示的頁面。
     * @return 切換頁面並要求重建控制項的按鈕。
     */
    private fun pageButton(x: Int, key: String, target: Page): RestartableMarqueeButtonWidget = RestartableMarqueeButtonWidget.builder(Component.translatable(key)) {
        if (page != target) {
            page = target
            rebuild()
        }
    }.dimensions(x, FormScreenLayout.PAGE_SWITCH_Y, FormScreenLayout.PAGE_SWITCH_WIDTH, FormScreenLayout.BUTTON_HEIGHT).build().also {
        it.outlined = page == target
    }

    /** 依牌桌占用狀態與本地成員身分建立房間頁的操作按鈕。 */
    private fun initRoomPage() {
        val table = stateStore.tableOccupancy(tableId) ?: return
        val room = stateStore.roomSnapshot(tableId)
        when (table.occupancy) {
            TableOccupancyDto.VACANT -> addFooterActions(listOf(ActionButton(MinecraftRoomScreenKeys.CREATE, RoomActionDto.Create(table.tableId), true)))
            TableOccupancyDto.GAME -> addFooterActions(emptyList())
            TableOccupancyDto.ROOM -> {
                if (room == null) return
                val actions = mutableListOf<ActionButton>()

                /**
                 * 將操作加入本次房間頁的按鈕清單。
                 *
                 * @param key 按鈕文字翻譯鍵。
                 * @param action 點擊後傳送的房間操作。
                 * @param active 按鈕是否可點擊。
                 * @param tooltipKey 按鈕提示的翻譯鍵；null 時不顯示提示。
                 * @return 清單加入結果。
                 */
                fun button(
                    key: String,
                    action: RoomActionDto,
                    active: Boolean = true,
                    tooltipKey: String? = null,
                ) = actions.add(ActionButton(key, action, active, tooltipKey))
                if (!room.isInRoom) {
                    button(MinecraftRoomScreenKeys.JOIN, RoomActionDto.Join(table.tableId), room.playerIds.size < room.gameConfig.ruleConfig.maxPlayers)
                } else if (room.isHost) {
                    button(MinecraftRoomScreenKeys.ADD_AI, RoomActionDto.AddAi(table.tableId), room.playerIds.size < room.gameConfig.ruleConfig.maxPlayers)
                    val spaceObstructed = tableSpaceObstructions.isObstructed(tableId)
                    button(
                        key = MinecraftRoomScreenKeys.START,
                        action = RoomActionDto.Start(table.tableId),
                        active = room.canStart && !spaceObstructed,
                        tooltipKey = MinecraftRoomScreenKeys.START_BLOCKED_TABLE_SPACE.takeIf { spaceObstructed },
                    )
                    button(MinecraftRoomScreenKeys.DISBAND, RoomActionDto.Disband(table.tableId))
                } else {
                    val selfId = minecraft?.player?.uuid?.let(UUID::toString)
                    val ready = room.readyPlayerIds.any { it.toString() == selfId }
                    button(
                        key = if (ready) MinecraftRoomScreenKeys.CANCEL_READY else MinecraftRoomScreenKeys.READY,
                        action = RoomActionDto.ToggleReady(table.tableId),
                    )
                    button(MinecraftRoomScreenKeys.LEAVE, RoomActionDto.Leave(table.tableId))
                }
                addMemberCardWidgets(table.tableId, room)
                addFooterActions(actions)
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
        memberScroll.clamp(grid.scrollbar.maximumScroll)
        val visibleRows = grid.visibleGridRows
        room.playerIds.forEachIndexed { index, targetId ->
            if (targetId == room.hostId) return@forEachIndexed
            val row = index / grid.columns
            if (row !in visibleRows) return@forEachIndexed
            val cardX = grid.cardLeft(index)
            val cardY = grid.cardTop(row)
            if (targetId in room.aiPlayerIds) {
                addAiStrategySelector(
                    tableIdText = tableIdText,
                    targetId = targetId,
                    current = room.aiPlayerStrategyKeys[targetId],
                    editable = room.isHost,
                    x = cardX + 8,
                    y = grid.statusTop(row) - AI_STRATEGY_BUTTON_LIFT,
                    width = grid.cardWidth - 20,
                )
            }
            if (!room.isHost) return@forEachIndexed
            addRenderableWidget(
                RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftRoomScreenKeys.KICK)) {
                    send(RoomActionDto.Kick(tableIdText, targetId.toString()))
                }.dimensions(cardX + grid.cardWidth - RoomMemberGridLayout.CARD_RIGHT_INSET - KICK_BUTTON_SIZE - KICK_BUTTON_INSET, cardY + KICK_BUTTON_INSET, KICK_BUTTON_SIZE, KICK_BUTTON_SIZE).build().also {
                    it.icon = PixelIcon.CROSS
                    it.tooltip = Tooltip.create(Component.translatable(MinecraftRoomScreenKeys.KICK))
                },
            )
        }
    }

    /**
     * 在底部四格按鈕列放入房間操作，關閉固定在最右一格，操作依序緊接在關閉左邊，主要操作因此靠近關閉。
     *
     * @param actions 依顯示順序排列的房間操作，最多三個。
     */
    private fun addFooterActions(actions: List<ActionButton>) {
        val grid = memberGrid(stateStore.roomSnapshot(tableId)?.playerIds?.size ?: 0)
        require(actions.size < RoomMemberGridLayout.FOOTER_SLOTS) { "Room footer has no slot left for Close" }
        val close = RoomMemberGridLayout.FOOTER_SLOTS - 1
        actions.forEachIndexed { index, action ->
            val slot = close - actions.size + index
            addActionButton(grid.footer.buttonX(slot), grid.footerY, grid.footer.buttonWidth(slot), action)
        }
        addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftRoomScreenKeys.CLOSE)) {
                closeEntireScreen()
            }.dimensions(grid.footer.buttonX(close), grid.footerY, grid.footer.buttonWidth(close), FormScreenLayout.BUTTON_HEIGHT).build(),
        )
    }

    /**
     * 在 AI 成員卡片上加入策略選擇器，點擊展開可選策略的清單；非房主只能檢視。
     *
     * @param tableIdText 傳送房間操作使用的牌桌識別碼。
     * @param targetId AI 成員的識別碼。
     * @param current 目前策略識別碼；未知時為 null。
     * @param editable 是否可以變更策略。
     * @param x 選擇器左界。
     * @param y 選擇器上界。
     * @param width 選擇器寬度。
     */
    private fun addAiStrategySelector(
        tableIdText: String,
        targetId: Uuid,
        current: String?,
        editable: Boolean,
        x: Int,
        y: Int,
        width: Int,
    ) {
        val keys = aiStrategies.getAllStrategyKeys().toList()
        val selectorKey = AiStrategySelectorKey(targetId)
        lateinit var selector: RestartableMarqueeButtonWidget
        selector = RestartableMarqueeButtonWidget.builder(aiStrategyText(current)) {
            dropdown.toggle(
                key = selectorKey,
                anchor = selector,
                options = keys.map { DropdownOption(label = aiStrategyText(it)) },
                selectedIndex = keys.indexOf(current).takeIf { it >= 0 },
            ) { index -> send(RoomActionDto.ChangeAiStrategy(tableIdText, targetId.toString(), keys[index])) }
        }.dimensions(x, y, width, AI_STRATEGY_BUTTON_HEIGHT).build()
        selector.active = editable && keys.isNotEmpty()
        selector.selectorExpanded = { dropdown.isOpenFor(selectorKey) }
        selector.tooltip = Tooltip.create(
            ControlTooltip.options(
                description = Component.translatable(MinecraftRoomScreenKeys.AI_STRATEGY_TITLE),
                labels = keys.map(::aiStrategyText),
                currentIndex = keys.indexOf(current),
            ),
        )
        addRenderableWidget(selector)
    }

    /**
     * 建立並加入一個房間操作按鈕。
     *
     * @param x 按鈕左界。
     * @param y 按鈕頂端。
     * @param width 按鈕寬度。
     * @param button 包含名稱、操作、啟用狀態與提示的按鈕設定。
     */
    private fun addActionButton(x: Int, y: Int, width: Int, button: ActionButton) {
        addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(button.key)) {
                send(button.action)
            }.dimensions(x, y, width, 20).build().also { widget ->
                widget.active = button.active
                button.tooltipKey?.let { widget.tooltip = Tooltip.create(Component.translatable(it)) }
            },
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
    private fun aiStrategyText(strategyKey: String?): Component = strategyKey?.let { key ->
        aiStrategyNames.find(key)?.let(Component::translatable) ?: Component.literal(key)
    } ?: Component.literal("AI")

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderScreenBackground(context, mouseX, mouseY, delta)
        renderContentBackground(context)
        ScreenRendering.title(context, font, title, width)
        val pointerX = dropdown.pointer(mouseX)
        val pointerY = dropdown.pointer(mouseY)
        val labelTooltip = when (page) {
            Page.ROOM -> {
                renderRoom(context, pointerX, pointerY)
                null
            }
            Page.SETTINGS -> settingsPage.renderContent(context, pointerX, pointerY)
        }
        renderWidgets(context, pointerX, pointerY, delta)
        labelTooltip?.let { context.renderTooltip(font, it, mouseX, mouseY) }
        dropdown.render(context, font, height, mouseX, mouseY)
    }

    override fun mouseWheelScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (dropdown.mouseScrolled(font, height, amount)) return true
        if (amount == 0.0) return super.mouseWheelScrolled(mouseX, mouseY, amount)
        if (page == Page.SETTINGS) return settingsPage.mouseScrolled(mouseX, mouseY, amount)
        if (page == Page.ROOM && stateStore.tableOccupancy(tableId)?.occupancy == TableOccupancyDto.GAME) {
            val grid = playingGrid()
            val players = sortedPlayingPlayers()
            val index = grid.cardIndexAt(mouseX, mouseY)
            val player = index?.let(players::getOrNull)
            if (index != null && player != null && grid.isOverInfo(index, mouseX, mouseY)) {
                val maximum = grid.maximumInfoScroll(infoRows(player).size)
                if (maximum > 0) {
                    infoScroll(player.playerId).scrollBy(amount, maximum)
                    return true
                }
            }
        }
        val grid = currentMemberGrid()
        if (page == Page.ROOM && grid != null && (grid.isOverGrid(mouseX, mouseY) || grid.isOverGridScrollbar(mouseX, mouseY))) {
            if (memberScroll.scrollBy(amount, grid.scrollbar.maximumScroll)) rebuild()
            return true
        }
        return super.mouseWheelScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (dropdown.mouseClicked(font, height, mouseX, mouseY, button)) return true
        if (page == Page.SETTINGS && settingsPage.mouseClicked(mouseX, mouseY, button)) return true
        if (page == Page.ROOM && button == 0) {
            if (stateStore.tableOccupancy(tableId)?.occupancy == TableOccupancyDto.GAME) {
                val playingGrid = playingGrid()
                val players = sortedPlayingPlayers()
                val index = playingGrid.cardIndexAt(mouseX, mouseY)
                val player = index?.let(players::getOrNull)
                if (index != null && player != null && playingGrid.isOverInfoScrollbar(index, mouseX, mouseY)) {
                    val total = infoRows(player).size
                    if (playingGrid.maximumInfoScroll(total) > 0) {
                        val scroll = infoScroll(player.playerId)
                        playingInfoDragPlayer = player.playerId
                        scroll.beginDrag(mouseY, playingGrid.infoScrollbar(index, total, scroll.index))
                        return true
                    }
                }
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
        if (dropdown.isOpen) return true
        if (page == Page.SETTINGS && settingsPage.mouseDragged(mouseY, button)) return true
        val draggedPlayer = playingInfoDragPlayer
        if (draggedPlayer != null && button == 0) {
            val players = sortedPlayingPlayers()
            val index = players.indexOfFirst { it.playerId == draggedPlayer }
            if (index >= 0) {
                val scroll = infoScroll(draggedPlayer)
                scroll.dragTo(mouseY, playingGrid().infoScrollbar(index, infoRows(players[index]).size, scroll.index))
            }
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
        settingsPage.mouseReleased()
        playingInfoDragPlayer?.let { infoScroll(it).endDrag() }
        playingInfoDragPlayer = null
        memberScroll.endDrag()
        return super.mouseReleased(mouseX, mouseY, button)
    }

    /** 下拉清單展開時不讓底層元件排入提示框。 */
    override fun setTooltipForNextRenderPass(lines: List<FormattedCharSequence>, positioner: ClientTooltipPositioner, focused: Boolean) {
        if (!dropdown.isOpen) super.setTooltipForNextRenderPass(lines, positioner, focused)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (dropdown.keyPressed(font, height, keyCode)) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean = dropdown.isOpen || super.charTyped(chr, modifiers)

    override fun tick() {
        super.tick()
        if (rebuildRequested) {
            rebuildRequested = false
            rebuildWidgets()
            return
        }
        if (!isTableStillAvailable()) {
            closeEntireScreen()
            return
        }
        if (tableSpaceObstructions.isObstructed(tableId) != lastSpaceObstructed) {
            lastSpaceObstructed = tableSpaceObstructions.isObstructed(tableId)
            rebuildWidgets()
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
            if (settingsPage.finishPendingApply()) page = Page.ROOM
            if (stateStore.tableOccupancy(tableId)?.occupancy == TableOccupancyDto.VACANT) {
                wasRoomMember = false
                page = Page.ROOM
                settingsPage.clearDraft()
            }
            rebuildWidgets()
            return
        }
        val authoritative = currentConfig() ?: return
        val outcome = settingsPage.syncAuthoritative(authoritative)
        if (outcome is RoomSettingsDraft.Outcome.Adopted) {
            if (outcome.returnToRoom) page = Page.ROOM
            rebuildWidgets()
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
        val player = minecraft?.player ?: return false
        val world = minecraft?.level ?: return false
        if (world.dimension().location().toString() != expectedDimension) return false
        if (player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) > 64.0) return false
        return world.getBlockEntity(BlockPos(x, y, z)) is MahjongTableController
    }

    /**
     * 依牌桌狀態繪製空桌說明、等待成員或對局成員。
     *
     * @param context 畫面繪製情境。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     */
    private fun renderRoom(context: GuiGraphics, mouseX: Int, mouseY: Int) {
        val table = stateStore.tableOccupancy(tableId) ?: return
        when (table.occupancy) {
            TableOccupancyDto.VACANT -> context.drawCenteredString(font, Component.translatable(MinecraftRoomScreenKeys.EMPTY), width / 2, 70, 0xFFFFFF)
            TableOccupancyDto.GAME -> {
                renderPlayingMembers(context, mouseX, mouseY)
                val grid = playingGrid()
                context.drawCenteredString(
                    font,
                    fitClientText(font, Component.translatable(MinecraftRoomScreenKeys.PLAYING), grid.contentRight - grid.contentLeft),
                    width / 2,
                    grid.statusY,
                    STATUS_COLOR,
                )
            }
            TableOccupancyDto.ROOM -> stateStore.roomSnapshot(tableId)?.let { room ->
                renderMembers(
                    context = context,
                    playerIds = room.playerIds,
                    aiIds = room.aiPlayerIds.toSet(),
                    memberStatus = MemberStatus.Waiting(room.readyPlayerIds.toSet(), room.hostId),
                    mouseX = mouseX,
                    mouseY = mouseY,
                )
            }
        }
    }

    /**
     * 進行中對局依權威自風排序，固定呈現東、南、西、北——卡片本身的大小與位置跟等待室
     * （[renderMembers]）共用同一套 grid、放大規則與捲動機制（[memberScroll]），差異只在名稱下方
     * 顯示的是 player info（[resolvePlayingPlayerInfo]）而不是加入狀態；資訊放不下時捲軸畫在該卡片內，每張卡片各自捲動。
     *
     * @param context 畫面繪製情境。
     * @param mouseX 游標水平座標，用於模型預覽朝向。
     * @param mouseY 游標垂直座標，用於模型預覽朝向。
     */
    private fun renderPlayingMembers(
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        val sortedPlayers = sortedPlayingPlayers()
        val grid = playingGrid()
        memberScroll.clamp(grid.scrollbar.maximumScroll)
        sortedPlayers.forEachIndexed { index, player ->
            val gridRow = index / grid.columns
            if (gridRow !in grid.visibleGridRows) return@forEachIndexed
            val x = grid.cardLeft(index)
            val y = grid.cardTop(gridRow)
            context.fill(x + 2, y, x + grid.cardWidth - RoomMemberGridLayout.CARD_RIGHT_INSET, y + grid.cardHeight, CardBackground.COLOR)
            val infoTop = grid.infoTop(gridRow)
            renderMemberAppearance(context, player.playerId, player.isAi, figureArea(grid, x, y, infoTop), mouseX, mouseY)
            context.drawCenteredString(
                font,
                fitClientText(font, Component.literal(playerInfoDisplayName(player) { id -> displayNames.resolve(tableId, id.toString(), true) }), grid.cardWidth - 12),
                x + grid.cardWidth / 2,
                grid.nameTop(infoTop),
                0xFFFFFF,
            )
            val rows = infoRows(player)
            val scroll = infoScroll(player.playerId)
            scroll.clamp(grid.maximumInfoScroll(rows.size))
            val scrollable = grid.maximumInfoScroll(rows.size) > 0
            val textWidth = grid.cardWidth - 12 - if (scrollable) INFO_SCROLLBAR_RESERVED_WIDTH * 2 else 0
            rows.drop(scroll.index).take(grid.visibleInfoRows).forEachIndexed { infoRowIndex, row ->
                context.drawCenteredString(
                    font,
                    fitClientText(font, row.first, textWidth),
                    x + grid.cardWidth / 2,
                    infoTop + infoRowIndex * RoomMemberGridLayout.INFO_ROW_HEIGHT,
                    row.second,
                )
            }
            if (scrollable) VerticalScrollbar.render(context, grid.infoScrollbarLeft(index), grid.infoScrollbar(index, rows.size, scroll.index))
        }
        renderMemberGridScrollbar(context, grid)
    }

    /** 依權威自風排成東、南、西、北的進行中對局玩家。 */
    private fun sortedPlayingPlayers(): List<MahjongPlayerInfoEntry> = resolvePlayingPlayerInfo().sortedBy { WIND_ORDER.getValue(it.seatWind) }

    /**
     * 取得玩家卡片內的資訊清單：自風、分數與公開指標。
     *
     * @param player 對局玩家。
     * @return 每一行的文字與顏色。
     */
    private fun infoRows(player: MahjongPlayerInfoEntry) = memberPresentation.infoRows(player, resolvePlayerInfoEntity()?.dealerPlayerId ?: stateStore.gameSnapshot(tableId)?.dealerPlayerId)

    /**
     * 取得玩家卡片資訊清單的捲動狀態；第一次使用時建立。
     *
     * @param playerId 玩家識別碼。
     * @return 這位玩家的捲動狀態。
     */
    private fun infoScroll(playerId: Uuid): ScrollState = playingInfoScrolls.getOrPut(playerId) { ScrollState() }

    /** 優先使用 Player Info entity 的完整公開快照；同步尚未抵達時以遊戲快照安全降級。 */
    private fun resolvePlayingPlayerInfo(): List<MahjongPlayerInfoEntry> {
        resolvePlayerInfoEntity()?.players?.takeIf { it.isNotEmpty() }?.let { return it }
        val snapshot = stateStore.gameSnapshot(tableId) ?: return emptyList()
        return roomMemberEntriesFrom(snapshot.players, stateStore.gameAiPlayerIds(tableId)) { id, isAi ->
            if (isAi) displayNames.resolve(tableId, id.toString(), true) else resolveLocalPlayerName(id)
        }
    }

    /**
     * 在牌桌周圍搜尋屬於此牌桌的玩家資訊實體。
     *
     * @return 第一個相符實體；世界、位置或實體尚未可用時為 null。
     */
    private fun resolvePlayerInfoEntity(): MahjongPlayerInfoEntity? {
        val table = stateStore.tableOccupancy(tableId) ?: return null
        val world = minecraft?.level ?: return null
        val x = table.tableX ?: return null
        val y = table.tableY ?: return null
        val z = table.tableZ ?: return null
        return world.getEntitiesOfClass(
            MahjongPlayerInfoEntity::class.java,
            AABB(x - 4.0, y - 4.0, z - 4.0, x + 5.0, y + 5.0, z + 5.0),
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
        context: GuiGraphics,
        playerIds: List<Uuid>,
        aiIds: Set<Uuid>,
        memberStatus: MemberStatus,
        mouseX: Int,
        mouseY: Int,
    ) {
        val grid = memberGrid(playerIds.size)
        memberScroll.clamp(grid.scrollbar.maximumScroll)
        val visibleRows = grid.visibleGridRows
        playerIds.forEachIndexed { index, playerId ->
            val row = index / grid.columns
            if (row !in visibleRows) return@forEachIndexed
            val x = grid.cardLeft(index)
            val y = grid.cardTop(row)
            context.fill(x + 2, y, x + grid.cardWidth - RoomMemberGridLayout.CARD_RIGHT_INSET, y + grid.cardHeight, CardBackground.COLOR)
            val ai = playerId in aiIds
            val statusTop = grid.statusTop(row)
            renderMemberAppearance(context, playerId, ai, figureArea(grid, x, y, statusTop), mouseX, mouseY)
            context.drawCenteredString(
                font,
                fitClientText(font, memberName(playerId, ai), grid.cardWidth - 12),
                x + grid.cardWidth / 2,
                grid.nameTop(statusTop),
                0xFFFFFF,
            )
            val status = when (memberStatus) {
                MemberStatus.Playing -> null
                is MemberStatus.Waiting -> when {
                    playerId == memberStatus.hostId -> Component.translatable(MinecraftRoomScreenKeys.HOST)
                    ai -> null
                    playerId in memberStatus.readyPlayerIds -> Component.translatable(MinecraftRoomScreenKeys.MEMBER_READY)
                    else -> Component.translatable(MinecraftRoomScreenKeys.MEMBER_NOT_READY)
                }
            }
            status?.let {
                val highlighted = memberStatus is MemberStatus.Waiting && playerId in memberStatus.readyPlayerIds
                context.drawCenteredString(font, it, x + grid.cardWidth / 2, statusTop, if (highlighted) 0x88FF88 else 0xAAAAAA)
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
     * @param area 卡片上畫人物的範圍與放大倍數。
     * @param mouseX 游標水平座標，用於模型朝向。
     * @param mouseY 游標垂直座標，用於模型朝向。
     */
    private fun renderMemberAppearance(
        context: GuiGraphics,
        playerId: Uuid,
        ai: Boolean,
        area: RoomFigureArea,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (area.modelScale == 0) {
            renderPortrait(context, playerId, ai, area)
            return
        }
        val character = characters.find(tableId)?.players?.get(playerId)
        val worldEntityId = characters.find(tableId)?.worldEntityIds?.get(playerId)
        val liveAvailable = if (ai) {
            worldEntityId != null && minecraft?.level?.entitiesForRendering()?.any { it.uuid == worldEntityId.toJavaUuid() && !it.isRemoved } == true
        } else {
            minecraft?.level?.getPlayerByUUID(playerId.toJavaUuid()) != null
        }
        val resolution = appearanceResolver.resolve(playerId, ai, character?.definition, liveAvailable)
        when (val warning = resolution.warning) {
            null -> Unit
            is RoomMemberAppearanceResolver.Warning.ProviderFailed ->
                logger.warn("Failed to resolve room member appearance provider {}", warning.providerId, warning.cause)
            is RoomMemberAppearanceResolver.Warning.MissingActorPreviewFactory ->
                logger.warn("No room actor preview factory is registered for {}; using portrait fallback", warning.actorKey)
        }
        if (resolution.actorKey != null || (ai && resolution.appearance == RoomMemberAppearanceResolver.Appearance.WorldEntityModel)) {
            val rendered = portraitRenderer.renderActorModel(
                snapshot = character,
                id = resolution.actorKey,
                context = context,
                area = area,
                mouseX = mouseX,
                mouseY = mouseY,
                worldEntityId = worldEntityId,
            )
            if (rendered) return
        }
        // 真人未載入時沿用玩家預覽，模型不可用時由頭像備援。
        val entity = if (!ai && (resolution.appearance == RoomMemberAppearanceResolver.Appearance.PlayerModel || resolution.appearance == RoomMemberAppearanceResolver.Appearance.WorldEntityModel)) {
            resolvePlayerPreview(playerId)
        } else {
            null
        }
        if (entity == null) {
            renderPortrait(context, playerId, ai, area)
            return
        }
        GuiEntityModelRenderer.render(
            entity = entity,
            context = context,
            area = area,
            mouseX = mouseX,
            mouseY = mouseY,
        )
    }

    /** 對局中面板的卡片版面；跟等待室共用同一套換欄規則與捲動機制。 */
    private fun playingGrid(): RoomMemberGridLayout = memberGrid(resolvePlayingPlayerInfo().size)

    /**
     * 只在卡片列超出可見範圍時繪製捲軸。
     *
     * @param context 畫面繪製情境。
     * @param grid 成員卡片版面幾何。
     */
    private fun renderMemberGridScrollbar(context: GuiGraphics, grid: RoomMemberGridLayout) {
        if (grid.scrollbar.maximumScroll == 0) return
        VerticalScrollbar.render(context, grid.scrollbarLeft, grid.scrollbar)
    }

    /**
     * 建立成員卡片上畫人物的範圍，腳底在成員名稱上方。
     *
     * @param grid 成員卡片版面幾何。
     * @param x 卡片左界。
     * @param y 卡片頂端。
     * @param labelTop 名稱下方第一行的頂端。
     * @return 人物範圍與卡片放大倍數。
     */
    private fun figureArea(grid: RoomMemberGridLayout, x: Int, y: Int, labelTop: Int): RoomFigureArea = RoomFigureArea(
        left = x,
        top = y,
        width = grid.cardWidth,
        feetY = grid.figureFeetY(labelTop),
        scale = grid.cardScale,
    )

    /**
     * 在卡片人物範圍內置中繪製成員頭像。
     *
     * @param context 畫面繪製情境。
     * @param playerId 成員識別碼。
     * @param isAi 是否使用 AI 外觀。
     * @param area 卡片上畫人物的範圍與放大倍數。
     */
    private fun renderPortrait(context: GuiGraphics, playerId: Uuid, isAi: Boolean, area: RoomFigureArea) {
        if (area.portraitSize == 0) return
        portraitRenderer.render(
            playerId = playerId,
            isAi = isAi,
            x = area.portraitLeft.toFloat(),
            y = area.portraitTop.toFloat(),
            size = area.portraitSize.toFloat(),
            alpha = 1f,
            z = 200f,
            matrices = context.pose(),
            consumers = Minecraft.getInstance().renderBuffers().bufferSource(),
        )
    }

    /** 取得房間設定，沒有房間快照時改用進行中對局的設定；兩者皆無時為 null。 */
    private fun currentConfig(): GameConfig? = stateStore.roomSnapshot(tableId)?.gameConfig
        ?: stateStore.tableOccupancy(tableId)?.playingGameConfig?.let { dto -> dto.toDomain(networkRegistries) }

    /**
     * 取得成員名稱，AI 使用指定順序的編號名稱，真人使用本地名稱解析。
     *
     * @param playerId 成員識別碼。
     * @param ai 是否由 AI 操控。
     * @return 名稱或尚未解析完成的替代文字。
     */
    private fun memberName(playerId: Uuid, ai: Boolean): Component = Component.literal(displayNames.resolve(tableId, playerId.toString(), ai))

    /**
     * 優先使用玩家列表或已解析的名稱；未知時要求解析器載入，依解析器的帳號政策決定是否外部查詢。
     *
     * @param playerId 真人玩家識別碼。
     * @return 已知名稱；載入中或無法解析時為 null。
     */
    private fun resolveLocalPlayerName(playerId: Uuid): String? {
        val name = minecraft?.connection?.getPlayerInfo(playerId.toJavaUuid())?.profile?.name
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
     * 目前畫面上實際顯示的卡片 grid 版面（等待室或進行中對局）；兩者共用同一套卡片版面與
     * [memberScroll] 捲動機制，不在這兩種狀態時為 null。
     */
    private fun currentMemberGrid(): RoomMemberGridLayout? = when (stateStore.tableOccupancy(tableId)?.occupancy) {
        TableOccupancyDto.ROOM -> stateStore.roomSnapshot(tableId)?.let { memberGrid(it.playerIds.size) }
        TableOccupancyDto.GAME -> playingGrid()
        else -> null
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
        val minecraft = minecraft ?: return null
        minecraft.level?.getPlayerByUUID(playerId.toJavaUuid())?.let { return it }
        val world = minecraft.level ?: return null
        val listEntry = minecraft.connection?.getPlayerInfo(playerId.toJavaUuid())
        val profile = listEntry?.profile ?: profileResolver.resolvedProfile(playerId)
        if (profile == null) {
            profileResolver.requestResolve(playerId)
            return profilePreviews.getOrPut(playerId) {
                OfflinePlayerPreviewEntity(world, GameProfile(playerId.toJavaUuid(), OFFLINE_PLAYER_PROFILE_NAME))
            }
        }
        profilePreviews[playerId]?.takeUnless { it is OfflinePlayerPreviewEntity }?.let { return it }
        return RemotePlayer(world, profile).also { profilePreviews[playerId] = it }
    }

    /** Esc 在設定頁返回成員頁，有尚未套用的修改時先確認；在成員頁才離開整個畫面。 */
    override fun onClose() {
        if (page == Page.SETTINGS) settingsPage.close() else closeEntireScreen()
    }

    /** 關閉整個 RoomScreen，不套用設定頁的階層式 Esc 行為。 */
    private fun closeEntireScreen() {
        minecraft?.setScreen(null)
    }

    /** 在目前輸入事件完成後安全重建 widgets，避免舊 widget 被重新設為 focus。 */
    private fun rebuild() {
        rebuildRequested = true
    }

    override fun isPauseScreen(): Boolean = false

    /** 設定頁借用本畫面能力的接口。 */
    private inner class SettingsPageHost : RoomSettingsPageHost {
        override val screen: Screen
            get() = this@RoomScreen

        override val textRenderer: Font
            get() = this@RoomScreen.font

        override fun <T> addWidget(widget: T): T where T : GuiEventListener, T : Renderable, T : NarratableEntry = addRenderableWidget(widget)

        override fun requestRebuild() = rebuild()

        override fun showRoomPage() {
            page = Page.ROOM
            rebuild()
        }

        override fun openScreen(screen: Screen) {
            minecraft?.setScreen(screen)
        }

        override fun openRuleCatalogue(context: RuleCatalogueBrowseContext) {
            minecraft?.setScreen(ruleCatalogue.createScreen(context = context, exit = RoomRuleCatalogueExit(this@RoomScreen)))
        }

        override fun currentConfig(): GameConfig? = this@RoomScreen.currentConfig()

        override val dropdown: DropdownOverlay
            get() = this@RoomScreen.dropdown
    }

    /**
     * 某個 AI 成員的策略選擇器在展開清單中的識別值。
     *
     * @property playerId AI 成員的識別碼。
     */
    private data class AiStrategySelectorKey(val playerId: Uuid)

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
     * @property tooltipKey 按鈕提示的翻譯鍵；null 時不顯示提示。
     */
    private data class ActionButton(
        val key: String,
        val action: RoomActionDto,
        val active: Boolean,
        val tooltipKey: String? = null,
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
        /** [resolvePlayerPreview] 為找不到 player-list profile 的玩家組出佔位 [GameProfile] 時使用的名稱。 */
        const val OFFLINE_PLAYER_PROFILE_NAME = "Offline"

        /** AI 策略選擇器的高度。 */
        const val AI_STRATEGY_BUTTON_HEIGHT = 18

        /** AI 策略選擇器上界在卡片資訊區上界之上的距離；選擇器放在名稱正下方。 */
        const val AI_STRATEGY_BUTTON_LIFT = 2

        /** 踢除按鈕的邊長。 */
        const val KICK_BUTTON_SIZE = 16

        /** 踢除按鈕與卡片底色右上角的距離。 */
        const val KICK_BUTTON_INSET = 4

        /** 卡片資訊可捲動時，文字左右各讓出的寬度，避免碰到卡片內的捲軸。 */
        const val INFO_SCROLLBAR_RESERVED_WIDTH = 6

        /** 底部按鈕列上方狀態文字的顏色。 */
        const val STATUS_COLOR = 0xAAAAAA

        /** 對局成員卡片依東、南、西、北排列的自風順序。 */
        val WIND_ORDER = mapOf(Wind.EAST to 0, Wind.SOUTH to 1, Wind.WEST to 2, Wind.NORTH to 3)
    }
}
