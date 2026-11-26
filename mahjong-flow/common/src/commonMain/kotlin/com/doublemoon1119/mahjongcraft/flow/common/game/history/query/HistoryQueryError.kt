package com.doublemoon1119.mahjongcraft.flow.common.game.history.query
import com.doublemoon1119.mahjongcraft.flow.common.error.ApplicationError

/**
 * 可預期的歷史查詢失敗。
 *
 * @property code 對外穩定錯誤代碼。
 * @property diagnosticMessage 英文診斷訊息。
 */
data class HistoryQueryError(
    val code: HistoryQueryErrorCode,
    val diagnosticMessage: String,
) : ApplicationError
