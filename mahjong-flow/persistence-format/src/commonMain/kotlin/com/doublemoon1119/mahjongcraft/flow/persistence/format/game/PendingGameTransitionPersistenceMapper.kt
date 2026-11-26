package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition

/**
 * 將領域層待完成流程轉換為 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun PendingGameTransition.toPersistenceDto(): PendingGameTransitionPersistenceDto = PendingGameTransitionPersistenceDto(
    type = when (this) {
        PendingGameTransition.AdvanceRound -> PendingGameTransitionPersistenceDto.Type.ADVANCE_ROUND
        PendingGameTransition.SettleExhaustiveDraw -> PendingGameTransitionPersistenceDto.Type.SETTLE_EXHAUSTIVE_DRAW
        PendingGameTransition.ReturnToRoom -> PendingGameTransitionPersistenceDto.Type.RETURN_TO_ROOM
    },
)

/**
 * 將 persistence DTO 還原為領域層待完成流程。
 *
 * @return 轉換後的結果。
 */
fun PendingGameTransitionPersistenceDto.toDomain(): PendingGameTransition = when (type) {
    PendingGameTransitionPersistenceDto.Type.ADVANCE_ROUND -> PendingGameTransition.AdvanceRound
    PendingGameTransitionPersistenceDto.Type.SETTLE_EXHAUSTIVE_DRAW -> PendingGameTransition.SettleExhaustiveDraw
    PendingGameTransitionPersistenceDto.Type.RETURN_TO_ROOM -> PendingGameTransition.ReturnToRoom
}
