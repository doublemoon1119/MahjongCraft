package com.doublemoon1119.mahjongcraft.flow.common.result

import com.doublemoon1119.mahjongcraft.flow.common.error.ApplicationError

/**
 * 應用層操作的執行結果封裝型別。
 *
 * 用於取代拋出例外來表達可預期的業務邏輯錯誤，提供編譯器保證的型別安全錯誤處理。
 *
 * 使用範例：
 * ```
 * fun doSomething(): Outcome<Room, RoomError> {
 *     if (condition) return Outcome.Error(RoomError.RoomNotFound(id))
 *     return Outcome.Success(room)
 * }
 * ```
 *
 * @param T 操作成功時的回傳值型別。
 * @param E 操作失敗時的錯誤型別，必須繼承 [ApplicationError]。
 */
sealed interface Outcome<out T, out E : ApplicationError> {

    /**
     * 操作成功的結果。
     *
     * @param value 成功時回傳的值。
     */
    data class Success<T>(val value: T) : Outcome<T, Nothing>

    /**
     * 操作失敗的結果。
     *
     * @param error 失敗時的錯誤實例。
     */
    data class Error<E : ApplicationError>(val error: E) : Outcome<Nothing, E>
}
