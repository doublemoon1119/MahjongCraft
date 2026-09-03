package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.state.AuthoritativeStatePersistenceDto
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * [Game] 中不屬於麻將規則桌況的可變 runtime 狀態。
 *
 * @property remainingReserveMillisByPlayerId 以玩家 UUID 字串索引的剩餘保留思考時間毫秒數。
 * @property forcedAutoPlayPlayerIds 已進入強制自動操作的玩家 UUID 字串集合。
 * @property isMatchOver 整場對局是否已結束，見 [Game.isMatchOver]。
 * @property pendingTransition 呈現結束後尚待完成的權威流程，見 [Game.pendingTransition]。
 * @property roundCompletion 最近一次本局結算的權威摘要。
 * @property matchEndReasonId 整場終局的完整 namespaced 原因；尚未終局時為 null。
 * @property hostId 開局時的房主 UUID 字串，見 [Game.hostId]；早於此欄位新增的既有存檔沒有這筆資料，
 *   還原時退回第一位玩家（見 [AuthoritativeStatePersistenceDto]）。
 * @property roomPlayerIds 開局前房間成員的固定顯示順序。
 * @property interruptedBaseMillisByPlayerId 因 server session 結束而中斷的那一次決策，其尚未使用的
 *   基本思考時間毫秒數，以玩家 UUID 字串索引；早於此欄位新增的既有存檔沒有這筆資料，
 *   還原時退回空 map。
 */
@Serializable
data class GameRuntimeStatePersistenceDto(
    val remainingReserveMillisByPlayerId: Map<String, Long>,
    val forcedAutoPlayPlayerIds: Set<String> = emptySet(),
    val isMatchOver: Boolean = false,
    val pendingTransition: PendingGameTransitionPersistenceDto? = null,
    val roundCompletion: RoundCompletionSummaryPersistenceDto? = null,
    val matchEndReasonId: String? = null,
    val pendingRoundPreparation: PendingRoundPreparationPersistenceDto? = null,
    val hostId: String? = null,
    val roomPlayerIds: List<String>? = null,
    val interruptedBaseMillisByPlayerId: Map<String, Long> = emptyMap(),
)

/** 將 [Game] 的 runtime 狀態轉換成 persistence DTO。 */
fun Game.toRuntimeStatePersistenceDto(): GameRuntimeStatePersistenceDto = GameRuntimeStatePersistenceDto(
    remainingReserveMillisByPlayerId = remainingReserveMillisByPlayerId.mapKeys { (playerId, _) -> playerId.toString() },
    forcedAutoPlayPlayerIds = forcedAutoPlayPlayerIds.mapTo(mutableSetOf(), Uuid::toString),
    isMatchOver = isMatchOver,
    pendingTransition = pendingTransition?.toPersistenceDto(),
    roundCompletion = roundCompletion?.toPersistenceDto(),
    matchEndReasonId = matchEndReasonId,
    pendingRoundPreparation = pendingRoundPreparation?.toPersistenceDto(),
    hostId = hostId.toString(),
    roomPlayerIds = roomPlayerIds.map(Uuid::toString),
    interruptedBaseMillisByPlayerId = interruptedBaseMillisByPlayerId.mapKeys { (playerId, _) -> playerId.toString() },
)

/** 將 persistence DTO 中的剩餘保留思考時間還原成以玩家 UUID 索引的資料。 */
fun GameRuntimeStatePersistenceDto.toRemainingReserveMillisByPlayerId(): Map<Uuid, Long> = remainingReserveMillisByPlayerId.mapKeys { (playerId, _) -> Uuid.parse(playerId) }

/** 將 persistence DTO 中的強制自動操作玩家還原成 UUID 集合。 */
fun GameRuntimeStatePersistenceDto.toForcedAutoPlayPlayerIds(): Set<Uuid> = forcedAutoPlayPlayerIds.mapTo(
    mutableSetOf(),
    Uuid::parse,
)

/** 將 persistence DTO 中的中斷基本思考時間還原成以玩家 UUID 索引的資料。 */
fun GameRuntimeStatePersistenceDto.toInterruptedBaseMillisByPlayerId(): Map<Uuid, Long> = interruptedBaseMillisByPlayerId.mapKeys { (playerId, _) -> Uuid.parse(playerId) }
