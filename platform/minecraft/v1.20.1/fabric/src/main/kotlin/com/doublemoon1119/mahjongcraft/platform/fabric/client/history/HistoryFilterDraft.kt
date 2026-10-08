package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto

/** 歷史篩選子頁共用的可變文字草稿；無效內容不會覆蓋最近一次有效查詢。 */
internal class HistoryFilterDraft {
    /** 原始輸入欄位，返回子頁時保留。 */
    var input: HistoryBrowseFilterInput = HistoryBrowseFilterInput()

    /** 最近一次驗證結果的欄位錯誤。 */
    var errors: Map<HistoryBrowseFilterField, HistoryBrowseFilterError> = emptyMap()

    /** 將目前範圍套用到草稿，並保留所有原始文字。 */
    fun setScope(scope: HistoryQueryScopeDto) {
        if (scope.coversAllMatches) {
            input = input.copy(ownRankMin = "", ownRankMax = "")
            errors = errors - HistoryBrowseFilterField.MIN_RANK - HistoryBrowseFilterField.MAX_RANK
        }
    }
}
