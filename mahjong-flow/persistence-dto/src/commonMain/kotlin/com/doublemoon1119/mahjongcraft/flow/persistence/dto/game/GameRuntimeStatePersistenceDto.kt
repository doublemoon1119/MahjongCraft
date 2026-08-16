package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * [Game] 中不屬於麻將規則桌況的可變 runtime 狀態。
 *
 * @property remainingReserveMillisByPlayerId 以玩家 UUID 字串索引的剩餘保留思考時間毫秒數。
 * @property forcedAutoPlayPlayerIds 已進入強制自動操作的玩家 UUID 字串集合。
 * @property isMatchOver 整場對局是否已結束，見 [Game.isMatchOver]。
 * @property hostId 開局時的房主 UUID 字串，見 [Game.hostId]；早於此欄位新增的既有存檔沒有這筆資料，
 *   還原時退回第一位玩家（見 [com.doublemoon1119.mahjongcraft.flow.persistence.dto.state.AuthoritativeStatePersistenceDto]）。
 */
@Serializable
data class GameRuntimeStatePersistenceDto(
    val remainingReserveMillisByPlayerId: Map<String, Long>,
    val forcedAutoPlayPlayerIds: Set<String> = emptySet(),
    val isMatchOver: Boolean = false,
    val hostId: String? = null,
)

/** 將 [Game] 的 runtime 狀態轉換成 persistence DTO。 */
fun Game.toRuntimeStatePersistenceDto(): GameRuntimeStatePersistenceDto = GameRuntimeStatePersistenceDto(
    remainingReserveMillisByPlayerId = remainingReserveMillisByPlayerId.mapKeys { (playerId, _) -> playerId.toString() },
    forcedAutoPlayPlayerIds = forcedAutoPlayPlayerIds.mapTo(mutableSetOf(), Uuid::toString),
    isMatchOver = isMatchOver,
    hostId = hostId.toString(),
)

/** 將 persistence DTO 中的剩餘保留思考時間還原成以玩家 UUID 索引的資料。 */
fun GameRuntimeStatePersistenceDto.toRemainingReserveMillisByPlayerId(): Map<Uuid, Long> = remainingReserveMillisByPlayerId.mapKeys { (playerId, _) -> Uuid.parse(playerId) }

/** 將 persistence DTO 中的強制自動操作玩家還原成 UUID 集合。 */
fun GameRuntimeStatePersistenceDto.toForcedAutoPlayPlayerIds(): Set<Uuid> = forcedAutoPlayPlayerIds.mapTo(
    mutableSetOf(),
    Uuid::parse,
)
