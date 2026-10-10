package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPendingKanDoraReveal
import kotlin.uuid.Uuid

/**
 * 將等待公開項目轉為 persistence DTO。
 *
 * @return 轉換後的待公開槓寶牌 persistence DTO。
 */
fun RiichiPendingKanDoraReveal.toPersistenceDto(): RiichiPendingKanDoraRevealPersistenceDto = RiichiPendingKanDoraRevealPersistenceDto(
    actorPlayerId.toString(),
    sourceKanType.toPersistenceDto(),
    supplementalDrawNumber,
)

/**
 * 將等待公開 persistence DTO 還原為 [RiichiPendingKanDoraReveal]。
 *
 * @return 還原後的待公開槓寶牌狀態。
 */
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
