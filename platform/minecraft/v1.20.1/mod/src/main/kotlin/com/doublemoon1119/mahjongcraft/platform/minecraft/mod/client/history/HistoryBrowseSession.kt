package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowseContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryTileSetAsideDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.catalogue.RuleCatalogueScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character.ClientAiCharacterPreviews
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.concurrency.ClientThreadCoroutineDispatcher
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.ClientHistoryRequestGate
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryArchiveStatusTransport
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryArchiveStatusView
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryBrowsePage
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryBrowseStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryFilterDraft
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryHandSorting
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryQueryTransport
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryReplayPlayback
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryReplayVisibility
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryRoundEventPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.ClientPlayerProfileResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tableprop.ClientTablePropPreviews
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
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
 * @property eventPresenter 單局事件頁與牌面頁共用的事件文字呈現。
 * @property setAsideDisplays 移出牌區標題來源。
 * @property discardMarkers 牌河公開標記的呈現方式來源。
 * @property settlementTemplates 結算明細欄位的規則專屬標題來源。
 * @property tileFaces 共用 GUI 牌面 renderer。
 * @property tileAssets 牌種與 Minecraft 素材的映射來源。
 * @property tileDisplayNames 牌面提示中牌名的查詢來源。
 * @property ruleCatalogue 從規則設定頁開啟規則一覽。
 * @property tileLayoutProfiles 規則的桌面牌位配置，供牌桌場景擺牌。
 * @property playerProfiles 牌桌場景中真人玩家模型的皮膚來源。
 * @property characterPreviews 牌桌場景中 AI 角色預覽模型的來源。
 * @property tablePropPreviews 牌桌場景中桌上物件的實體來源。
 * @property clientConfig 玩家的顯示設定，例如牌桌場景的最新捨牌與同種牌提示。
 * @param dispatcher 客戶端主執行緒排程。
 * @property parent 關閉後返回的原畫面，指令入口為 null。
 * @param initialMatchId 聊天入口指定的對局；null 表示一般列表。
 * @property onClosed 通知開啟控制器解除此 session。
 * @param requestGate 同一 client 連線共用的歷史查詢送出 gate。
 */
internal class HistoryBrowseSession(
    transport: HistoryQueryTransport,
    archiveStatus: HistoryArchiveStatusTransport,
    val participants: HistoryParticipantPresentationResolver,
    val ruleNames: RuleModuleDisplayNameRegistry,
    val configResolver: GameConfigPresentationResolver,
    val networkRegistries: NetworkDtoRegistries,
    private val moduleRegistry: MahjongModuleRegistry,
    val eventPresenter: HistoryRoundEventPresenter,
    val setAsideDisplays: HistoryTileSetAsideDisplayRegistry,
    val discardMarkers: HistoryDiscardMarkerDisplayRegistry,
    val settlementTemplates: WinSettlementPresentationTemplateRegistry,
    val tileFaces: MahjongTileFaceRenderer,
    val tileAssets: MinecraftTileAssetRegistry,
    val tileDisplayNames: TileDisplayNameRegistry,
    private val ruleCatalogue: RuleCatalogueScreenController,
    val tileLayoutProfiles: TableTileLayoutProfileRegistry,
    val playerProfiles: ClientPlayerProfileResolver,
    val characterPreviews: ClientAiCharacterPreviews,
    val tablePropPreviews: ClientTablePropPreviews,
    val clientConfig: MahjongClientConfigStore,
    dispatcher: ClientThreadCoroutineDispatcher,
    private val parent: Screen?,
    initialMatchId: String?,
    private val onClosed: () -> Unit,
    requestGate: ClientHistoryRequestGate,
) {
    /** 此瀏覽獨立的主執行緒工作。 */
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** 指定結算對局的保存狀態監看器。 */
    val archiveStatusController = HistoryArchiveStatusController(archiveStatus, scope, requestGate = requestGate)

    /** 列表、摘要、規則設定與單局內容共用的權威查詢結果。 */
    val controller = HistoryBrowseController(transport = transport, parentScope = scope, json = NetworkJson(networkRegistries), requestGate = requestGate, beforeDetailQuery = {
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
    private var moduleConfig: GameConfigDto? = null

    /** 由 [moduleConfig] 取得的規則模組；設定缺少、無法解碼或沒有安裝時為 null。 */
    private var cachedModule: MahjongRuleModule<*>? = null

    /** 最近排好手牌的回放桌況；同一份桌況與牌序不重複排序。 */
    private val sortedStates = LinkedHashMap<SortedStateKey, HistoryRoundStateDto>()

    /**
     * 取得單一玩家的排序選擇。
     *
     * @param state 目前歷史桌況。
     * @param seat 玩家初始座位。
     * @return 是否選擇理牌順序。
     */
    fun isHandSorted(state: HistoryRoundStateDto, seat: Int): Boolean = handSorting.isSorted(state.identity.matchId, handPreferencePlayer(state, seat))

    /**
     * 切換單一玩家的手牌顯示，不修改保存資料。
     *
     * @param state 目前歷史桌況。
     * @param seat 玩家初始座位。
     */
    fun toggleHandSorting(state: HistoryRoundStateDto, seat: Int) {
        handSorting.toggle(state.identity.matchId, handPreferencePlayer(state, seat))
    }

    /**
     * 取得玩家顯示偏好鍵；沒有 UUID 的 AI 使用穩定初始座位。
     *
     * @param state 同場歷史身分。
     * @param seat 玩家初始座位。
     * @return 玩家 UUID 或不與 UUID 重疊的座位鍵。
     * @throws NoSuchElementException 歷史身分中不存在指定初始座位時拋出。
     */
    private fun handPreferencePlayer(state: HistoryRoundStateDto, seat: Int): String = state.identity.players.first { it.initialSeatIndex == seat }.playerId ?: "seat:$seat"

    /**
     * 取得立牌顯示順序，摸入牌仍由呼叫端獨立排列。
     *
     * @param state 目前歷史桌況與牌目錄。
     * @param player 欲排列的玩家。
     * @return 原始或依該場規則排序的牌索引。
     */
    fun sortedHandTiles(state: HistoryRoundStateDto, player: HistoryReplayPlayerStateDto): List<Int> {
        val order = handOrder(state)
        return if (isHandSorted(state, player.initialSeatIndex)) handSorting.orderedTiles(player, state.tileCatalog, order) else player.handTiles
    }

    /**
     * 回放用的桌況：每一家立著的手牌都依該場規則的牌序排好，最近摸的牌仍獨立放在摸牌位；規則牌序還不能用時照原本順序。
     *
     * @param state 目前歷史桌況與牌目錄。
     * @return 手牌排好的桌況。
     */
    fun replayState(state: HistoryRoundStateDto): HistoryRoundStateDto {
        val order = handOrder(state) ?: return state
        val key = SortedStateKey(state, order)
        sortedStates[key]?.let { return it }
        val sorted = state.copy(players = state.players.map { handSorting.sortedPlayer(it, state.tileCatalog, order) })
        sortedStates[key] = sorted
        while (sortedStates.size > SORTED_STATE_CACHE_SIZE) sortedStates.remove(sortedStates.keys.first())
        return sorted
    }

    /**
     * 確認同場規則牌序已可使用。
     *
     * @param state 目前歷史桌況。
     * @return 是否可切換理牌順序。
     */
    fun canSortHand(state: HistoryRoundStateDto): Boolean = handOrder(state) != null

    /**
     * 這台電腦是否裝有某場對局使用的規則，例如只裝在伺服器上的第三方規則不算。
     *
     * @param ruleId 規則模組識別碼；摘要沒有記錄時為 null。
     * @return 裝有該規則或無從判斷時為 true。
     */
    fun ruleInstalled(ruleId: String?): Boolean = ruleId == null || ruleId in moduleRegistry.getAllModuleIds()

    /**
     * 取得這場對局的規則模組，供牌桌場景擺牌。
     *
     * @param state 目前歷史桌況。
     * @return 規則模組；規則設定還沒讀到或無法解碼時為 null。
     */
    fun ruleModule(state: HistoryRoundStateDto): MahjongRuleModule<*>? {
        val config = controller.state.value.ruleSettings?.takeIf { it.matchId == state.identity.matchId }?.config
        if (config != moduleConfig) {
            moduleConfig = config
            cachedModule = config?.let { runCatching { moduleRegistry.getModule(it.toDomain(networkRegistries).ruleConfig) }.getOrNull() }
        }
        return cachedModule
    }

    /**
     * 取得同場規則牌序，不套用其他對局設定。
     *
     * @param state 目前歷史桌況。
     * @return 已註冊牌序，或設定缺少／無法解碼時為 null。
     */
    private fun handOrder(state: HistoryRoundStateDto): TileOrder? = ruleModule(state)?.tileOrder

    /**
     * 依當前查詢摘要的公開規則取得牌組外觀，不使用牌目錄或手牌推測規則。
     *
     * @param matchId 要顯示的對局；未指定時使用當前摘要。
     * @return 該場的公開外觀；摘要不符或缺少規則時使用內建預設。
     */
    fun tileAppearance(matchId: String? = null): MinecraftTileSetAppearanceDefinition {
        val summary = controller.state.value.summary?.detail?.summary
            ?.takeIf { matchId == null || it.matchId == matchId }
        return summary?.ruleId?.let(tileAssets::findAppearanceForRule) ?: BuiltInMinecraftTileAppearances.DEFAULT
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

    /**
     * 開啟目前摘要的規則設定唯讀頁。
     *
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
        navigate(HistoryRoundEventsScreen(this, tileFaces, tileAssets, eventPresenter, settlementTemplates))
        return true
    }

    /**
     * 開啟已確認交易的完整牌面頁。
     *
     * @param position 初始狀態或已確認交易的位置。
     * @return 是否接受導航。
     */
    fun openRoundState(position: HistoryRoundPositionDto): Boolean {
        if (closed || !controller.showRoundState(position)) return false
        navigate(HistoryRoundStateScreen(this))
        return true
    }

    /** 回放的播放狀態；同一次瀏覽中換局或重開回放時沿用速度。 */
    val replayPlayback = HistoryReplayPlayback()

    /**
     * 從單局事件頁開始回放，停在指定步數並暫停。
     *
     * @param step 開始的步數：0 是初始牌面，k 是第 k - 1 筆交易完成後。
     * @return 是否接受導航。
     */
    fun openReplay(step: Int): Boolean {
        if (closed || !controller.showReplay(step)) return false
        replayPlayback.roundChanged()
        navigate(HistoryReplayScreen(this))
        return true
    }

    /**
     * 結束回放，回到目前局的事件頁。
     *
     * @return 是否接受返回。
     */
    fun backFromReplay(): Boolean {
        if (closed || !controller.backFromReplay()) return false
        replayPlayback.pause()
        navigate(HistoryRoundEventsScreen(this, tileFaces, tileAssets, eventPresenter, settlementTemplates))
        return true
    }

    /**
     * 返回原單局事件頁，不重送已成功的事件查詢。
     *
     * @return 是否接受返回。
     */
    fun backToRound(): Boolean {
        if (closed || !controller.backToRound()) return false
        navigate(HistoryRoundEventsScreen(this, tileFaces, tileAssets, eventPresenter, settlementTemplates))
        return true
    }

    /** 瀏覽是否仍在進行；結束後不再接受導航。 */
    val isOpen: Boolean get() = !closed

    /** 回放場景各項顯示的設定，在這次瀏覽中沿用。 */
    val replayVisibility = HistoryReplayVisibility()

    /**
     * 從回放畫面開啟可見性子頁；回放畫面保留原狀，完成後回到它。
     *
     * @param replay 目前的回放畫面。
     * @param context 回放場景的座位與視角。
     * @return 是否已開啟。
     */
    fun openReplayVisibility(replay: HistoryReplayScreen, context: HistoryReplayVisibilityContext): Boolean {
        if (closed || screen !== replay) return false
        navigate(HistoryReplayVisibilityScreen(this, replay, context))
        return true
    }

    /**
     * 從可見性子頁回到原回放畫面；瀏覽已結束時關回遊戲。
     *
     * @param replay 開啟可見性子頁的回放畫面。
     */
    fun backToReplay(replay: HistoryReplayScreen) {
        if (closed) {
            Minecraft.getInstance().setScreen(null)
            return
        }
        navigate(replay)
    }

    /**
     * 從規則設定頁開啟規則一覽；切換期間不結束瀏覽，規則一覽成為此瀏覽目前的畫面。
     *
     * @param settings 目前顯示的規則設定頁。
     * @param context 規則與歷史對局設定。
     * @return 是否已開啟。
     */
    fun openRuleCatalogue(settings: Screen, context: RuleCatalogueBrowseContext): Boolean {
        if (closed || screen !== settings) return false
        navigate(ruleCatalogue.createScreen(context = context, exit = HistoryRuleCatalogueExit(session = this, settings = settings)))
        return true
    }

    /**
     * 從規則一覽返回原規則設定頁，保留其捲動位置；瀏覽已結束時關回遊戲。
     *
     * @param settings 開啟規則一覽的規則設定頁。
     */
    fun backFromRuleCatalogue(settings: Screen) {
        if (closed) {
            Minecraft.getInstance().setScreen(null)
            return
        }
        navigate(settings)
    }

    /**
     * 從歷史子頁返回摘要，保留摘要查詢與捲動位置。
     *
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

    /** 關閉整個瀏覽時是否回到開啟它的畫面；從指令、快捷鍵或聊天開啟時直接回到遊戲。 */
    val returnsToScreen: Boolean get() = parent != null

    /** 關閉整個瀏覽並返回開啟它的畫面或遊戲。 */
    fun close() {
        if (closed) return
        dispose()
        Minecraft.getInstance().setScreen(parent)
    }

    /**
     * 非本 session 導航造成的畫面替換也必須結束工作。
     *
     * @param removed 被 Minecraft 移除的畫面。
     */
    fun removed(removed: Screen) {
        if (!navigating && removed === screen) dispose()
    }

    /** 補查牌序所需規則、監看指定對局的封存狀態並在保存後刷新；原連線失效時關閉，不返回舊世界畫面。 */
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
            val client = Minecraft.getInstance()
            val ownsScreen = client.screen === screen
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
            Minecraft.getInstance().setScreen(next)
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

/** 留著的排好手牌桌況份數：目前這一步，以及動畫前後兩步。 */
private const val SORTED_STATE_CACHE_SIZE = 4

/**
 * 排好手牌的桌況快取鍵；桌況與牌序以同一個物件比對，不逐項比較內容。
 *
 * @property state 原本的桌況。
 * @property order 規則牌序。
 */
private class SortedStateKey(val state: HistoryRoundStateDto, val order: TileOrder) {
    /**
     * 是否為同一份桌況與同一個牌序。
     *
     * @param other 比較對象。
     * @return 兩者都是同一個物件時為 true。
     */
    override fun equals(other: Any?): Boolean = other is SortedStateKey && other.state === state && other.order === order

    /**
     * 依物件身分計算的雜湊值。
     *
     * @return 雜湊值。
     */
    override fun hashCode(): Int = HASH_MULTIPLIER * System.identityHashCode(state) + System.identityHashCode(order)
}

/** 合併兩個雜湊值時的乘數。 */
private const val HASH_MULTIPLIER = 31
