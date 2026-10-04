package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.concurrency.ClientThreadCoroutineDispatcher
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 同一次歷史瀏覽的畫面導航與工作生命週期。
 *
 * @param transport 歷史查詢配對邊界。
 * @param archiveStatus 單場保存狀態查詢邊界。
 * @property participants 共用玩家名稱與頭像來源。
 * @property ruleNames 規則顯示名稱 registry。
 * @property configResolver 以已註冊呈現定義解析歷史規則設定。
 * @property networkRegistries 解碼歷史規則設定所需的正式網路註冊表。
 * @property moduleRegistry 提供已保存規則設定的牌面顯示順序。
 * @property actionVocabulary 歷史動作的規則專屬名稱來源。
 * @property exhaustiveDrawReasons 流局原因名稱來源。
 * @property settlementTemplates 結算明細欄位的規則專屬標題來源。
 * @property tileFaces 共用 GUI 牌面 renderer。
 * @property tileAssets 牌種與 Minecraft 素材的映射來源。
 * @param dispatcher 客戶端主執行緒排程。
 * @property parent 關閉後返回的原畫面，指令入口為 null。
 * @param initialMatchId 聊天入口指定的對局；null 表示一般列表。
 * @property onClosed 通知開啟控制器解除此 session。
 */
internal class HistoryBrowseSession(
    transport: HistoryQueryTransport,
    archiveStatus: HistoryArchiveStatusTransport,
    val participants: HistoryParticipantPresentationResolver,
    val ruleNames: RuleModuleDisplayNameRegistry,
    val configResolver: GameConfigPresentationResolver,
    val networkRegistries: NetworkDtoRegistries,
    private val moduleRegistry: MahjongModuleRegistry,
    val actionVocabulary: GameActionVocabularyRegistry,
    val exhaustiveDrawReasons: ExhaustiveDrawReasonDisplayNameRegistry,
    val settlementTemplates: WinSettlementPresentationTemplateRegistry,
    val tileFaces: MahjongTileFaceRenderer,
    val tileAssets: MinecraftTileAssetRegistry,
    dispatcher: ClientThreadCoroutineDispatcher,
    private val parent: Screen?,
    initialMatchId: String?,
    private val onClosed: () -> Unit,
) {
    /** 此瀏覽獨立的主執行緒工作。 */
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** 指定結算對局的保存狀態監看器。 */
    val archiveStatusController = HistoryArchiveStatusController(archiveStatus, scope)

    /** 列表、摘要、規則設定與單局內容共用的權威查詢結果。 */
    val controller = HistoryBrowseController(transport, scope, beforeDetailQuery = {
        archiveMonitoringPaused = true
        archiveStatusController.pause()
    })

    /** 返回列表時是否需要恢復保存狀態監看。 */
    private var archiveMonitoringPaused = false

    /** 保留未完成輸入，子頁切換不丟棄文字。 */
    val filterDraft = HistoryFilterDraft()

    /** 本次瀏覽中每位玩家獨立的手牌顯示選擇。 */
    private val handSorting = HistoryHandSorting()

    /** 上次解析的規則設定，避免每幀重複解碼。 */
    private var sortingConfig: GameConfigDto? = null

    /** 已解析的規則牌序；缺少規則時保留原始順序。 */
    private var sortingOrder: TileOrder? = null

    /**
     * 取得單一玩家的排序選擇。
     * @param state 目前歷史桌況。
     * @param seat 玩家初始座位。
     * @return 是否選擇理牌順序。
     */
    fun isHandSorted(state: HistoryRoundStateDto, seat: Int): Boolean = handSorting.isSorted(state.identity.matchId, handPreferencePlayer(state, seat))

    /**
     * 切換單一玩家的手牌顯示，不修改保存資料。
     * @param state 目前歷史桌況。
     * @param seat 玩家初始座位。
     */
    fun toggleHandSorting(state: HistoryRoundStateDto, seat: Int) {
        handSorting.toggle(state.identity.matchId, handPreferencePlayer(state, seat))
    }

    /**
     * 取得玩家顯示偏好鍵；沒有 UUID 的 AI 使用穩定初始座位。
     * @param state 同場歷史身分。
     * @param seat 玩家初始座位。
     * @return 玩家 UUID 或不與 UUID 重疊的座位鍵。
     */
    private fun handPreferencePlayer(state: HistoryRoundStateDto, seat: Int): String = state.identity.players.first { it.initialSeatIndex == seat }.playerId ?: "seat:$seat"

    /**
     * 取得立牌顯示順序，摸入牌仍由呼叫端獨立排列。
     * @param state 目前歷史桌況與牌目錄。
     * @param player 欲排列的玩家。
     * @return 原始或依該場規則排序的牌索引。
     */
    fun sortedHandTiles(state: HistoryRoundStateDto, player: HistoryReplayPlayerStateDto): List<Int> {
        val order = handOrder(state)
        return if (isHandSorted(state, player.initialSeatIndex)) handSorting.orderedTiles(player, state.tileCatalog, order) else player.handTiles
    }

    /**
     * 確認同場規則牌序已可使用。
     * @param state 目前歷史桌況。
     * @return 是否可切換理牌順序。
     */
    fun canSortHand(state: HistoryRoundStateDto): Boolean = handOrder(state) != null

    /**
     * 快取並解析同場規則牌序，不套用其他對局設定。
     * @param state 目前歷史桌況。
     * @return 已註冊牌序，或設定缺少／無法解碼時為 null。
     */
    private fun handOrder(state: HistoryRoundStateDto): TileOrder? {
        val settings = controller.state.value.ruleSettings?.takeIf { it.matchId == state.identity.matchId }
        val config = settings?.config
        if (config != sortingConfig) {
            sortingConfig = config
            sortingOrder = config?.let { runCatching { moduleRegistry.getModule(it.toDomain(networkRegistries).ruleConfig).tileOrder }.getOrNull() }
        }
        return sortingOrder
    }

    /** 正常子頁切換期間，removed 不代表關閉瀏覽。 */
    private var navigating = false

    /** 結束後不再操作原畫面或接受回呼。 */
    private var closed = false

    /** 此 session 目前擁有的畫面。 */
    private var screen: Screen? = null

    /** 聊天入口指定的對局；不改變目前查詢條件。 */
    private var requestedMatchId: String? = initialMatchId

    /** 已對本次封存完成通知觸發的列表重新整理。 */
    private var archiveRefreshMatchId: String? = null

    /** 避免列表首次查詢尚未完成時同時占用保存狀態查詢配額。 */
    private var archiveStatusStarted = false

    /** 保存完成回覆後的准入冷卻起點，避免自動刷新與狀態查詢相撞。 */
    private var archiveSavedAt: TimeMark? = null

    /** 建立首次列表並只查詢一次。 */
    fun open() {
        if (closed) return
        navigate(HistoryListScreen(this))
        controller.open()
    }

    /**
     * 將目前瀏覽切換至指定結算對局，保留使用者已套用的篩選及排序。
     *
     * @param matchId 欲優先查找的對局 UUID；null 表示只開啟一般列表。
     */
    fun openMatchResult(matchId: String?) {
        if (closed) return
        requestedMatchId = matchId
        archiveRefreshMatchId = null
        archiveStatusStarted = false
        archiveSavedAt = null
        archiveStatusController.close()
        if (controller.state.value.page != HistoryBrowsePage.LIST) backToList()
        if (controller.state.value.list.status != HistoryBrowseStatus.Loading) controller.refresh()
    }

    /** 目前聊天入口指定的對局，供列表呈現保存狀態提示。 */
    fun requestedMatchId(): String? = requestedMatchId

    /** 重新查詢聊天入口指定對局的保存狀態。 */
    fun retryArchiveStatus() = archiveStatusController.retry()

    /** 開啟篩選子頁，保留查詢與列表位置。 */
    fun openFilters() {
        if (!closed && controller.showFilters()) navigate(HistoryFilterScreen(this))
    }

    /**
     * 開啟目前成功列表中的單場摘要，查詢與快取由共用 controller 管理。
     *
     * @param matchId 可見卡片對應的對局識別碼。
     * @return 是否已接受並切換至摘要畫面。
     */
    fun openSummary(matchId: String): Boolean {
        if (closed || !controller.showSummary(matchId)) return false
        navigate(HistorySummaryScreen(this))
        return true
    }

    /** 開啟目前摘要的規則設定唯讀頁。
     * @return 是否接受導航並開啟規則設定頁。
     */
    fun openRuleSettings(): Boolean {
        if (closed || !controller.showRuleSettings()) return false
        navigate(HistoryRuleSettingsScreen(this))
        return true
    }

    /**
     * 開啟摘要已公開的單局事件頁，保留同局原有分頁與捲動位置。
     *
     * @param roundNumber 摘要中的局序號。
     * @return 是否已接受導航並開啟單局紀錄。
     */
    fun openRound(roundNumber: Int): Boolean {
        if (closed || !controller.showRound(roundNumber)) return false
        navigate(HistoryRoundEventsScreen(this, tileFaces, tileAssets, HistoryRoundEventPresenter(actionVocabulary, exhaustiveDrawReasons), settlementTemplates))
        return true
    }

    /** 開啟已確認交易的完整牌面頁。
     * @param position 初始狀態或已確認交易的位置。
     * @return 是否接受導航。
     */
    fun openRoundState(position: HistoryRoundPositionDto): Boolean {
        if (closed || !controller.showRoundState(position)) return false
        navigate(HistoryRoundStateScreen(this))
        return true
    }

    /** 返回原單局事件頁，不重送已成功的事件查詢。
     * @return 是否接受返回。
     */
    fun backToRound(): Boolean {
        if (closed || !controller.backToRound()) return false
        navigate(HistoryRoundEventsScreen(this, tileFaces, tileAssets, HistoryRoundEventPresenter(actionVocabulary, exhaustiveDrawReasons), settlementTemplates))
        return true
    }

    /** 從歷史子頁返回摘要，保留摘要查詢與捲動位置。
     * @return 是否返回原摘要頁。
     */
    fun backToSummary(): Boolean {
        if (closed || !controller.backToSummary()) return false
        navigate(HistorySummaryScreen(this))
        return true
    }

    /** 返回列表，不重送已完成查詢。 */
    fun backToList() {
        if (!closed && controller.backToList()) {
            if (archiveMonitoringPaused) {
                archiveMonitoringPaused = false
                archiveStatusStarted = false
            }
            navigate(HistoryListScreen(this))
        }
    }

    /** 關閉整個瀏覽並返回原設定頁或遊戲。 */
    fun close() {
        if (closed) return
        dispose()
        MinecraftClient.getInstance().setScreen(parent)
    }

    /**
     * 非本 session 導航造成的畫面替換也必須結束工作。
     *
     * @param removed 被 Minecraft 移除的畫面。
     */
    fun removed(removed: Screen) {
        if (!navigating && removed === screen) dispose()
    }

    /** 原連線失效後隱藏資料，不返回舊世界的設定頁。 */
    fun tick() {
        controller.ensureRoundStateRuleSettings()
        val matchId = requestedMatchId.takeIf { controller.state.value.page == HistoryBrowsePage.LIST }
        if (matchId != null && !archiveStatusStarted && controller.state.value.list.status != HistoryBrowseStatus.Loading) {
            archiveStatusStarted = true
            archiveStatusController.watch(matchId)
        }
        if (matchId != null &&
            archiveRefreshMatchId != matchId &&
            archiveStatusController.view.value == HistoryArchiveStatusView.Resolved(HistoryArchiveStatusDto.SAVED) &&
            controller.state.value.list.status != HistoryBrowseStatus.Loading
        ) {
            val savedAt = archiveSavedAt ?: TimeSource.Monotonic.markNow().also { archiveSavedAt = it }
            if (savedAt.elapsedNow() >= controller.minimumInterval && controller.refresh()) {
                archiveRefreshMatchId = matchId
            }
        }
        if (!closed && controller.state.value.closed) {
            val client = MinecraftClient.getInstance()
            val ownsScreen = client.currentScreen === screen
            dispose()
            if (ownsScreen) client.setScreen(null)
        }
    }

    /**
     * 明確標記子頁切換，避免 removed 提早取消 controller。
     *
     * @param next 欲顯示的同 session 畫面。
     */
    private fun navigate(next: Screen) {
        navigating = true
        try {
            MinecraftClient.getInstance().setScreen(next)
            screen = next
        } finally {
            navigating = false
        }
    }

    /** 只取消本 session，不替換外部新畫面。 */
    private fun dispose() {
        if (closed) return
        closed = true
        controller.close()
        archiveStatusController.close()
        scope.cancel()
        onClosed()
    }
}
