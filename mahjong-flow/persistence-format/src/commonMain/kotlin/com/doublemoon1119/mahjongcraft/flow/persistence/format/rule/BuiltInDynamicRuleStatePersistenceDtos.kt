package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPendingKanDoraReveal
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** [RiichiDynamicState] 的完整 persistence DTO。 */
@Serializable
data class RiichiDynamicStatePersistenceDto(
    val riichiStickCount: Int,
    val completedSupplementalDrawCount: Int,
    val revealedKanDoraCount: Int,
    val pendingKanDoraReveals: List<RiichiPendingKanDoraRevealPersistenceDto>,
    val completedNorthDrawCount: Int,
)

/** 尚未正式公開的日麻槓寶牌 persistence DTO。 */
@Serializable
data class RiichiPendingKanDoraRevealPersistenceDto(
    val actorPlayerId: String,
    val sourceKanType: RiichiPendingKanTypePersistenceDto,
    val supplementalDrawNumber: Int,
)

/** Persistence 層保存的日麻來源槓牌種類。 */
@Serializable
enum class RiichiPendingKanTypePersistenceDto {
    /** 大明槓。 */
    OPEN_KAN,

    /** 暗槓。 */
    CLOSED_KAN,

    /** 加槓。 */
    ADDED_KAN,
}

/** 將等待公開項目轉為 persistence DTO。 */
fun RiichiPendingKanDoraReveal.toPersistenceDto(): RiichiPendingKanDoraRevealPersistenceDto = RiichiPendingKanDoraRevealPersistenceDto(
    actorPlayerId.toString(),
    sourceKanType.toPersistenceDto(),
    supplementalDrawNumber,
)

/** 將等待公開 persistence DTO 還原為 domain model。 */
fun RiichiPendingKanDoraRevealPersistenceDto.toDomain(): RiichiPendingKanDoraReveal = RiichiPendingKanDoraReveal(
    Uuid.parse(actorPlayerId),
    sourceKanType.toDomain(),
    supplementalDrawNumber,
)

/** 將來源槓牌種類轉為 persistence DTO。 */
private fun GameAction.KanType.toPersistenceDto(): RiichiPendingKanTypePersistenceDto = when (this) {
    GameAction.KanType.OPEN_KAN -> RiichiPendingKanTypePersistenceDto.OPEN_KAN
    GameAction.KanType.CLOSED_KAN -> RiichiPendingKanTypePersistenceDto.CLOSED_KAN
    GameAction.KanType.ADDED_KAN -> RiichiPendingKanTypePersistenceDto.ADDED_KAN
}

/** 將 persistence DTO 還原為來源槓牌種類。 */
private fun RiichiPendingKanTypePersistenceDto.toDomain(): GameAction.KanType = when (this) {
    RiichiPendingKanTypePersistenceDto.OPEN_KAN -> GameAction.KanType.OPEN_KAN
    RiichiPendingKanTypePersistenceDto.CLOSED_KAN -> GameAction.KanType.CLOSED_KAN
    RiichiPendingKanTypePersistenceDto.ADDED_KAN -> GameAction.KanType.ADDED_KAN
}
