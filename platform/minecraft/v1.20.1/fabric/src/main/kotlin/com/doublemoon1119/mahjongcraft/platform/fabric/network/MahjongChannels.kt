package com.doublemoon1119.mahjongcraft.platform.fabric.network

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTimerUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameCommandEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSelectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSubmissionResultDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.SnapshotClearedPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.TableOccupancyPayloadDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryQuerySettingsPayload
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerIdentityPayload
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

/** `mahjongcraft:` 命名空間下實際使用的命令、事件更新與主動快照同步頻道。 */
object MahjongChannels {
    /** 加入伺服器及設定重載後同步的歷史查詢操作間隔。 */
    val historyQuerySettings = S2CChannel("history_query_settings", HistoryQuerySettingsPayload.serializer(), HistoryQueryLimits.REQUEST_BYTES)

    /** 已授權快照／歷史參與者的有界名稱呈現資料。 */
    val playerIdentity = S2CChannel("player_identity", PlayerIdentityPayload.serializer(), PlayerIdentityPayload.MAX_BYTES)

    /** 有界保存狀態要求，伺服器驗證場次與查閱身分。 */
    val historyArchiveStatusRequest = C2SChannel("history_archive_status_request", HistoryArchiveStatusRequestDto.serializer(), HistoryQueryLimits.REQUEST_BYTES)

    /** 不包含牌面或內部診斷的保存狀態回應。 */
    val historyArchiveStatusResponse = S2CChannel("history_archive_status_response", HistoryArchiveStatusResponseDto.serializer(), HistoryQueryLimits.RESPONSE_BYTES)

    /** 有界歷史清單要求，不允許攜帶他人的查詢身分。 */
    val historyListRequest = C2SChannel("history_list_request", HistoryListRequestDto.serializer(), HistoryQueryLimits.REQUEST_BYTES)

    /** 有界歷史清單回應，不含手牌、牌山或原始事件。 */
    val historyListResponse = S2CChannel("history_list_response", HistoryListResponseDto.serializer(), HistoryQueryLimits.RESPONSE_BYTES)

    /** 有界單場摘要要求，伺服器仍須驗證公開條件。 */
    val historySummaryRequest = C2SChannel("history_summary_request", HistorySummaryRequestDto.serializer(), HistoryQueryLimits.REQUEST_BYTES)

    /** 有界單場摘要與局級時間索引回應。 */
    val historySummaryResponse = S2CChannel("history_summary_response", HistorySummaryResponseDto.serializer(), HistoryQueryLimits.RESPONSE_BYTES)

    /** 延遲查閱指定歷史場次開局設定的有界要求。 */
    val historyRuleSettingsRequest = C2SChannel("history_rule_settings_request", HistoryRuleSettingsRequestDto.serializer(), HistoryQueryLimits.REQUEST_BYTES)

    /** 僅包含歷史開局設定或安全錯誤碼的有界回應。 */
    val historyRuleSettingsResponse = S2CChannel("history_rule_settings_response", HistoryRuleSettingsResponseDto.serializer(), HistoryQueryLimits.RESPONSE_BYTES)
    val gameCommand = C2SChannel("game_command", GameCommandEnvelopeDto.serializer())
    val decisionSelection = C2SChannel("decision_selection", PlayerDecisionSelectionDto.serializer())

    val roomAction = C2SChannel("room_action", RoomActionDto.serializer())

    /**
     * 玩家（重新）加入世界後，主動要求伺服器重建一份目前歸屬的房間／對局快照，見
     * `PlayerConnectionLifecycleService.onSnapshotRequested`——由客戶端自己決定「我剛加入、還沒有任何
     * 快照」這件事並主動詢問，不依賴伺服器猜測何時該推送，理由見該方法 KDoc。沒有實際內容，用
     * `Unit` 表示純粹的請求信號。
     */
    val requestSnapshot = C2SChannel("request_snapshot", Unit.serializer())

    /**
     * 玩家重新加入世界時恢復伺服器記憶體中的自動理牌偏好；這個同步不得移動或翻起任何手牌。
     */
    val restoreAutoSortHand = C2SChannel("restore_auto_sort_hand", Boolean.serializer())

    /**
     * 玩家實際切換「自動整理手牌」偏好時送出，見 `MahjongCraftMod.registerSetAutoSortHandReceiver`／
     * `SetHandSortPreferenceUseCase`——手牌 tile entity 是伺服器端共用的實體，這個偏好必須讓伺服器
     * 知道才能實際重新排列座標，不像牌角標籤那種純客戶端疊加。
     */
    val setAutoSortHand = C2SChannel("set_auto_sort_hand", Boolean.serializer())
    val automaticControlUpdate = C2SChannel(
        "automatic_control_update",
        AutomaticControlUpdateRequestDto.serializer(),
    )
    val decisionTimerUpdate = S2CChannel("decision_timer_update", DecisionTimerUpdatePayloadDto.serializer())
    val decisionSubmissionResult = S2CChannel("decision_submission_result", PlayerDecisionSubmissionResultDto.serializer())
    val gameUpdate = S2CChannel("game_update", GameUpdatePayloadDto.serializer())

    val roomUpdate = S2CChannel("room_update", RoomUpdatePayloadDto.serializer())
    val gameSnapshot = S2CChannel("game_snapshot", GameSnapshotSyncPayloadDto.serializer())
    val roomSnapshot = S2CChannel("room_snapshot", RoomSnapshotSyncPayloadDto.serializer())
    val tableOccupancy = S2CChannel("table_occupancy", TableOccupancyPayloadDto.serializer())

    /**
     * 房間與對局都不存在時清除該玩家手上的快照，見 `ObserverSnapshotSender`。與 [tableOccupancy] 不同，
     * 這個頻道不會開啟任何畫面，收到時只更新已保存的狀態。
     */
    val snapshotCleared = S2CChannel("snapshot_cleared", SnapshotClearedPayloadDto.serializer())
    val automaticControlUpdateResult = S2CChannel(
        "automatic_control_update_result",
        AutomaticControlUpdateResultDto.serializer(),
    )
    val automaticControlSnapshot = S2CChannel(
        "automatic_control_snapshot",
        AutomaticControlSnapshotDto.serializer().nullable,
    )
}
