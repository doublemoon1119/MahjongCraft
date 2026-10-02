package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.concurrency.ClientThreadCoroutineDispatcher
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
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
    dispatcher: ClientThreadCoroutineDispatcher,
    private val parent: Screen?,
    initialMatchId: String?,
    private val onClosed: () -> Unit,
) {
    /** 此瀏覽獨立的主執行緒工作。 */
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** 列表與篩選共用的權威查詢結果。 */
    val controller = HistoryBrowseController(transport, scope)

    /** 指定結算對局的保存狀態監看器。 */
    val archiveStatusController = HistoryArchiveStatusController(archiveStatus, scope)

    /** 保留未完成輸入，子頁切換不丟棄文字。 */
    val filterDraft = HistoryFilterDraft()

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

    /** 返回列表，不重送已完成查詢。 */
    fun backToList() {
        if (!closed && controller.backToList()) navigate(HistoryListScreen(this))
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
        val matchId = requestedMatchId
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
