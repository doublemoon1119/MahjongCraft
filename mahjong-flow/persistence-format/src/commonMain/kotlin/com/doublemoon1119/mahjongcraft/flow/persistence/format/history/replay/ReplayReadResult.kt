package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

/**
 * Replay 讀取作業的結果。
 *
 * @param T 成功時產生的值型別。
 */
sealed class ReplayReadResult<out T> {
    /**
     * 成功完成 Replay 讀取的結果。
     *
     * @param T 讀取值的型別。
     * @property value 讀取出的值。
     * @property diagnostics 讀取作業的資源統計。
     */
    data class Success<out T>(val value: T, val diagnostics: ReplayReadDiagnostics) : ReplayReadResult<T>()

    /**
     * Replay 讀取失敗的結果。
     *
     * @property error 失敗原因。
     */
    data class Failure(val error: ReplayReadError) : ReplayReadResult<Nothing>()
}
