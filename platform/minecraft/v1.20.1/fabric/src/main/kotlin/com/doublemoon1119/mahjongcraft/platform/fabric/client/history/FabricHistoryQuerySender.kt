package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/**
 * 使用 Fabric 歷史查詢頻道傳送要求的實作。
 *
 * @property json 歷史查詢 DTO 的線路編碼器。
 */
@Single(binds = [HistoryQuerySender::class])
class FabricHistoryQuerySender(
    private val json: Json,
) : HistoryQuerySender {
    /**
     * 將清單要求交給 Fabric 客戶端頻道。
     *
     * @param request 已具備配對識別碼的清單要求。
     */
    override fun sendList(request: HistoryListRequestDto) {
        MahjongChannels.historyListRequest.sendToServer(json, request)
    }

    /**
     * 將摘要要求交給 Fabric 客戶端頻道。
     *
     * @param request 已具備配對識別碼的摘要要求。
     */
    override fun sendSummary(request: HistorySummaryRequestDto) {
        MahjongChannels.historySummaryRequest.sendToServer(json, request)
    }

    /** 將規則設定要求交給 Fabric 客戶端頻道。
     * @param request 已具備配對識別碼的規則設定要求。
     */
    override fun sendRuleSettings(request: HistoryRuleSettingsRequestDto) {
        MahjongChannels.historyRuleSettingsRequest.sendToServer(json, request)
    }

    /** 將單局事件頁要求交給 Fabric 客戶端頻道。
     * @param request 已具備配對識別碼的事件頁要求。
     */
    override fun sendRoundEvents(request: HistoryRoundEventsRequestDto) {
        MahjongChannels.historyRoundEventsRequest.sendToServer(json, request)
    }

    /** 將單局桌況要求交給 Fabric 客戶端頻道。
     * @param request 已具備配對識別碼的桌況要求。
     */
    override fun sendRoundState(request: HistoryRoundStateRequestDto) {
        MahjongChannels.historyRoundStateRequest.sendToServer(json, request)
    }
}
