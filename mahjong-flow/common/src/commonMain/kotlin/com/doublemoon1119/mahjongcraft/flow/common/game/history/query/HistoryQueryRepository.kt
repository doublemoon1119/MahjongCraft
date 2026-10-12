package com.doublemoon1119.mahjongcraft.flow.common.game.history.query
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundEvents
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState

/** Flow 層提供給平台適配器的歷史資料查詢邊界。 */
interface HistoryQueryRepository {
    /**
     * 依授權範圍、條件與 keyset 游標讀取一頁摘要。
     *
     * @param access 可信任的發起者資訊。
     * @param request 清單查詢要求。
     * @return 查詢頁或穩定錯誤。
     */
    suspend fun list(
        access: HistoryQueryAccess,
        request: HistoryListRequest,
    ): HistoryQueryResult<HistoryListPage>

    /**
     * 讀取單一已公開對局的摘要，不解碼完整重播內容。
     *
     * @param access 可信任的發起者資訊。
     * @param request 摘要查詢要求。
     * @return 對局詳情或穩定錯誤。
     */
    suspend fun summary(
        access: HistoryQueryAccess,
        request: HistorySummaryRequest,
    ): HistoryQueryResult<HistoryMatchDetail>

    /**
     * 讀取單一已公開對局的開局規則設定，不回傳目前房間設定。
     *
     * @param access 可信任的發起者資訊。
     * @param request 規則設定查詢要求。
     * @return 對局規則設定或穩定錯誤。
     */
    suspend fun ruleSettings(
        access: HistoryQueryAccess,
        request: HistoryRuleSettingsRequest,
    ): HistoryQueryResult<HistoryRuleSettings>

    /**
     * 讀取已公開單局的有界事件頁；未覆寫此方法的實作預設回傳 `NOT_AVAILABLE`。
     *
     * @param access 可信任的發起者資訊。
     * @param request 單局事件查詢要求。
     * @return 事件頁或穩定錯誤。
     */
    suspend fun roundEvents(
        access: HistoryQueryAccess,
        request: HistoryRoundEventsRequest,
    ): HistoryQueryResult<HistoryRoundEvents> = HistoryQueryResult.Failure(
        HistoryQueryError(HistoryQueryErrorCode.NOT_AVAILABLE, "Round event queries are not available"),
    )

    /**
     * 讀取已公開單局指定位置的完整桌況；未覆寫此方法的實作預設回傳 `NOT_AVAILABLE`。
     *
     * @param access 可信任的發起者資訊。
     * @param request 單局桌況查詢要求。
     * @return 桌況或穩定錯誤。
     */
    suspend fun roundState(
        access: HistoryQueryAccess,
        request: HistoryRoundStateRequest,
    ): HistoryQueryResult<HistoryRoundState> = HistoryQueryResult.Failure(
        HistoryQueryError(HistoryQueryErrorCode.NOT_AVAILABLE, "Round state queries are not available"),
    )
}
