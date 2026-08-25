package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundTransitionDirective
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
 * @property roundTransitionDirective 最近一次結算明確指定的莊家推進決策。
 * @property hostId 開局時的房主 UUID 字串，見 [Game.hostId]；早於此欄位新增的既有存檔沒有這筆資料，
 *   還原時退回第一位玩家（見 [AuthoritativeStatePersistenceDto]）。
 */
@Serializable
data class GameRuntimeStatePersistenceDto(
    val remainingReserveMillisByPlayerId: Map<String, Long>,
    val forcedAutoPlayPlayerIds: Set<String> = emptySet(),
    val isMatchOver: Boolean = false,
    val pendingTransition: PendingGameTransitionPersistenceDto? = null,
    val roundTransitionDirective: RoundTransitionDirectivePersistenceDto? = null,
    val hostId: String? = null,
)

/** 將 [Game] 的 runtime 狀態轉換成 persistence DTO。 */
fun Game.toRuntimeStatePersistenceDto(): GameRuntimeStatePersistenceDto = GameRuntimeStatePersistenceDto(
    remainingReserveMillisByPlayerId = remainingReserveMillisByPlayerId.mapKeys { (playerId, _) -> playerId.toString() },
    forcedAutoPlayPlayerIds = forcedAutoPlayPlayerIds.mapTo(mutableSetOf(), Uuid::toString),
    isMatchOver = isMatchOver,
    pendingTransition = pendingTransition?.toPersistenceDto(),
    roundTransitionDirective = roundTransitionDirective?.toPersistenceDto(),
    hostId = hostId.toString(),
)

/** 將 persistence DTO 中的剩餘保留思考時間還原成以玩家 UUID 索引的資料。 */
fun GameRuntimeStatePersistenceDto.toRemainingReserveMillisByPlayerId(): Map<Uuid, Long> = remainingReserveMillisByPlayerId.mapKeys { (playerId, _) -> Uuid.parse(playerId) }

/** 將 persistence DTO 中的強制自動操作玩家還原成 UUID 集合。 */
fun GameRuntimeStatePersistenceDto.toForcedAutoPlayPlayerIds(): Set<Uuid> = forcedAutoPlayPlayerIds.mapTo(
    mutableSetOf(),
    Uuid::parse,
)

/** 本局莊家推進決策的持久化類型。 */
@Serializable
enum class RoundTransitionDirectivePersistenceDto {
    REPEAT_DEALER,
    ADVANCE_DEALER,
}

/** 將領域層的莊家推進決策轉為 persistence DTO。 */
fun RoundTransitionDirective.toPersistenceDto(): RoundTransitionDirectivePersistenceDto = when (this) {
    RoundTransitionDirective.REPEAT_DEALER -> RoundTransitionDirectivePersistenceDto.REPEAT_DEALER
    RoundTransitionDirective.ADVANCE_DEALER -> RoundTransitionDirectivePersistenceDto.ADVANCE_DEALER
}

/** 將 persistence DTO 還原為領域層莊家推進決策。 */
fun RoundTransitionDirectivePersistenceDto.toDomain(): RoundTransitionDirective = when (this) {
    RoundTransitionDirectivePersistenceDto.REPEAT_DEALER -> RoundTransitionDirective.REPEAT_DEALER
    RoundTransitionDirectivePersistenceDto.ADVANCE_DEALER -> RoundTransitionDirective.ADVANCE_DEALER
}
