package com.doublemoon1119.mahjongcraft.flow.server.game.repository

/**
 * [GameRepository.withExpectedGame] 的結果。
 *
 * @param T 命令的回傳型別。
 */
sealed interface ExpectedGameResult<out T> {
    /**
     * 命令已在預期的遊戲上執行完畢。
     *
     * @property value 命令的回傳值。
     */
    data class Applied<T>(val value: T) : ExpectedGameResult<T>

    /** 寫入前權威遊戲已不是預期的遊戲；命令沒有寫入這一局，並在寫入時被中止。 */
    data object Stale : ExpectedGameResult<Nothing>
}
