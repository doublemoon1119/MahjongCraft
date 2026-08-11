package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * [Game] 中不屬於麻將規則桌況的可變 runtime 狀態。
 *
 * @property remainingReserveMillisByPlayerId 以玩家 UUID 字串索引的剩餘 B 時間毫秒數。
 */
@Serializable
data class GameRuntimeStatePersistenceDto(
    val remainingReserveMillisByPlayerId: Map<String, Long>,
)

/** 將 [Game] 的 runtime 狀態轉換成 persistence DTO。 */
fun Game.toRuntimeStatePersistenceDto(): GameRuntimeStatePersistenceDto = GameRuntimeStatePersistenceDto(
    remainingReserveMillisByPlayerId = remainingReserveMillisByPlayerId.mapKeys { (playerId, _) -> playerId.toString() },
)

/** 將 persistence DTO 中的剩餘 B 時間還原成以玩家 UUID 索引的資料。 */
fun GameRuntimeStatePersistenceDto.toRemainingReserveMillisByPlayerId(): Map<Uuid, Long> = remainingReserveMillisByPlayerId.mapKeys { (playerId, _) -> Uuid.parse(playerId) }
