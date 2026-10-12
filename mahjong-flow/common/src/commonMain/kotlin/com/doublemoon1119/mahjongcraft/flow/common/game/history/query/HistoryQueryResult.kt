package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/**
 * 歷史查詢 repository 的成功或失敗結果。
 *
 * @param T 查詢成功時回傳的資料型別。
 */
sealed interface HistoryQueryResult<out T> {
    /**
     * 查詢成功。
     *
     * @param T 查詢成功時回傳的資料型別。
     * @property value 已授權且經查詢限制處理的結果。
     */
    data class Success<T>(val value: T) : HistoryQueryResult<T>

    /**
     * 查詢失敗。
     *
     * @property error 穩定錯誤代碼及內部英文診斷。
     */
    data class Failure(val error: HistoryQueryError) : HistoryQueryResult<Nothing>
}
