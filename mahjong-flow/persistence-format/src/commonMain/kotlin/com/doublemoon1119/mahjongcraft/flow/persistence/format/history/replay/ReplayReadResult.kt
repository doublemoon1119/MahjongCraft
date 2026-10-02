package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

/** Replay 讀取作業失敗時可回報的錯誤種類。 */
enum class ReplayReadError {
    /** 文件內容不符合格式契約。 */
    INVALID_DOCUMENT,

    /** 文件使用目前讀取器不支援的內容。 */
    UNSUPPORTED_CONTENT,

    /** 文件超出讀取資源上限。 */
    LIMIT_EXCEEDED,

    /** 請求的選取項目不存在。 */
    SELECTION_NOT_FOUND,
}

/** Replay 讀取作業的結果。
 *
 * @param T 成功時產生的值型別。
 */
sealed class ReplayReadResult<out T> {
    /** 成功完成 Replay 讀取的結果。
     *
     * @param T 讀取值的型別。
     * @property value 讀取出的值。
     * @property diagnostics 讀取作業的資源統計。
     */
    data class Success<out T>(val value: T, val diagnostics: ReplayReadDiagnostics) : ReplayReadResult<T>()

    /** Replay 讀取失敗的結果。
     *
     * @property error 失敗原因。
     */
    data class Failure(val error: ReplayReadError) : ReplayReadResult<Nothing>()
}

/** Replay 讀取作業的資源統計。
 *
 * @property workUnits 消耗的工作單位數。
 * @property expandedNodes 展開處理的節點總數。
 * @property expandedStringBytes 展開字串的 UTF-8 位元組總數。
 * @property transactionsRebuilt 重建的交易數。
 */
data class ReplayReadDiagnostics(
    val workUnits: Long,
    val expandedNodes: Long,
    val expandedStringBytes: Long,
    val transactionsRebuilt: Int,
)
