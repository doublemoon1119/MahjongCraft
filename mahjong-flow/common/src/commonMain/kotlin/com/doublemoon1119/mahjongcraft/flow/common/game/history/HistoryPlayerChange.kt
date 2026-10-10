package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer

/**
 * 玩家狀態與累積動作紀錄的局部變化。
 *
 * @property playerWithoutHistory 交易後玩家狀態；[MahjongPlayer.actionHistory] 固定為空，避免反覆保存累積紀錄。
 * @property retainedActionCount 從交易前動作紀錄保留的前綴張數；清空紀錄時為零。
 * @property appendedActions 本次交易新追加的動作。
 */
data class HistoryPlayerChange(
    val playerWithoutHistory: MahjongPlayer,
    val retainedActionCount: Int,
    val appendedActions: List<GameAction>,
) {
    init {
        require(playerWithoutHistory.actionHistory.isEmpty()) { "History player change must not contain cumulative actions" }
        require(retainedActionCount >= 0) { "Retained history action count must not be negative" }
    }

    /** 使用前一位玩家的動作紀錄重建交易後玩家狀態。 */
    fun applyTo(before: MahjongPlayer): MahjongPlayer {
        require(before.id == playerWithoutHistory.id) { "History player change references another player" }
        require(retainedActionCount <= before.actionHistory.size) { "History action prefix exceeds source history" }
        return playerWithoutHistory.copy(
            actionHistory = before.actionHistory.take(retainedActionCount) + appendedActions,
        )
    }
}
