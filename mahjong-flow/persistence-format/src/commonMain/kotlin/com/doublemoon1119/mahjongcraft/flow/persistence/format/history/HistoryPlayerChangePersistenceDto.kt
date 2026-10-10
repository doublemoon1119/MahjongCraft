package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.GameActionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.MahjongPlayerPersistenceDto
import kotlinx.serialization.Serializable

/**
 * 玩家狀態與動作紀錄的局部變化。
 *
 * @property playerWithoutHistory 交易後的玩家狀態；累積動作紀錄固定為空。
 * @property retainedActionCount 從交易前動作紀錄保留的前綴數量。
 * @property appendedActions 本次交易新增的動作。
 */
@Serializable
data class HistoryPlayerChangePersistenceDto(
    val playerWithoutHistory: MahjongPlayerPersistenceDto,
    val retainedActionCount: Int,
    val appendedActions: List<GameActionPersistenceDto>,
)
