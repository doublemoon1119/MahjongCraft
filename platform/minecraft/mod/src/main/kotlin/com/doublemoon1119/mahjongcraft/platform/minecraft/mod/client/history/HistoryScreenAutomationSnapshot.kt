package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto

/**
 * 牌譜瀏覽目前的狀態，供開發工具判斷畫面是否已經載入完成。
 *
 * @property page 目前所在的頁面。
 * @property loading 目前頁面是否還在等伺服器回應。
 * @property failed 目前頁面的查詢是否失敗。
 * @property listedMatchIds 列表目前這一頁的對局識別碼。
 * @property roundCount 摘要中的局數；還沒開啟或還沒載入摘要時為 null。
 * @property replayStep 回放目前的步數：0 是初始牌面，k 是第 k - 1 筆交易完成後；不在回放時為 null。
 * @property replayStepReady 回放目前這一步的牌面是否已經載入。
 * @property replayTransactions 回放這一局已取得的全部交易；整局事件還沒全部取得時為空。
 */
data class HistoryScreenAutomationSnapshot(
    val page: HistoryScreenAutomationPage,
    val loading: Boolean,
    val failed: Boolean,
    val listedMatchIds: List<String>,
    val roundCount: Int?,
    val replayStep: Int?,
    val replayStepReady: Boolean,
    val replayTransactions: List<HistoryReplayTransactionDto>,
)
