package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.persistence.format.room.toPersistenceDto
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * 將 [Game] 的 runtime 狀態轉換成 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun Game.toRuntimeStatePersistenceDto(): GameRuntimeStatePersistenceDto = GameRuntimeStatePersistenceDto(
    remainingReserveByPlayerId = remainingReserveByPlayerId.mapKeys { (playerId, _) -> playerId.toString() },
    forcedAutoPlayPlayerIds = forcedAutoPlayPlayerIds.mapTo(mutableSetOf(), Uuid::toString),
    enabledAutomaticControlIdsByPlayerId = enabledAutomaticControlIdsByPlayerId.mapKeys { (playerId, _) ->
        playerId.toString()
    },
    automaticControlRevision = automaticControlRevision,
    isMatchOver = isMatchOver,
    pendingTransition = pendingTransition?.toPersistenceDto(),
    roundCompletion = roundCompletion?.toPersistenceDto(),
    matchEndReasonId = matchEndReasonId,
    pendingRuleDecision = pendingRuleDecision?.toPersistenceDto(),
    hostId = hostId.toString(),
    roomPlayerIds = roomPlayerIds.map(Uuid::toString),
    roomAdmission = roomAdmission.toPersistenceDto(),
    interruptedBaseByPlayerId = interruptedBaseByPlayerId.mapKeys { (playerId, _) -> playerId.toString() },
    matchId = matchId.toString(),
    aiPlayerStrategyKeys = aiPlayerStrategyKeys.mapKeys { (playerId, _) -> playerId.toString() },
)

/**
 * 將 persistence DTO 中的 AI 策略 key 還原成以玩家 UUID 索引的資料。
 *
 * @return 轉換後的結果。
 */
fun GameRuntimeStatePersistenceDto.toAiPlayerStrategyKeys(): Map<Uuid, String> = aiPlayerStrategyKeys.mapKeys { (playerId, _) -> Uuid.parse(playerId) }

/**
 * 將 persistence DTO 中的剩餘保留思考時間還原成以玩家 UUID 索引的資料。
 *
 * @return 轉換後的結果。
 */
fun GameRuntimeStatePersistenceDto.toRemainingReserveByPlayerId(): Map<Uuid, Duration> = remainingReserveByPlayerId.mapKeys { (playerId, _) -> Uuid.parse(playerId) }

/**
 * 將 persistence DTO 中的強制自動操作玩家還原成 UUID 集合。
 *
 * @return 轉換後的結果。
 */
fun GameRuntimeStatePersistenceDto.toForcedAutoPlayPlayerIds(): Set<Uuid> = forcedAutoPlayPlayerIds.mapTo(
    mutableSetOf(),
    Uuid::parse,
)

/**
 * 將 persistence DTO 中的本局自動操作控制還原成以玩家 UUID 索引的資料。
 *
 * @return 轉換後的結果。
 */
fun GameRuntimeStatePersistenceDto.toEnabledAutomaticControlIdsByPlayerId(): Map<Uuid, Set<String>> = enabledAutomaticControlIdsByPlayerId.mapKeys { (playerId, _) -> Uuid.parse(playerId) }

/**
 * 將 persistence DTO 中的中斷基本思考時間還原成以玩家 UUID 索引的資料。
 *
 * @return 轉換後的結果。
 */
fun GameRuntimeStatePersistenceDto.toInterruptedBaseByPlayerId(): Map<Uuid, Duration> = interruptedBaseByPlayerId.mapKeys { (playerId, _) -> Uuid.parse(playerId) }
