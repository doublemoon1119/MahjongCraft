package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import kotlin.uuid.Uuid

/**
 * 將領域層的下一任莊家轉為 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun RoundTransitionDirective.toPersistenceDto(): RoundTransitionDirectivePersistenceDto = when (this) {
    RoundTransitionDirective.RepeatDealer -> RoundTransitionDirectivePersistenceDto.RepeatDealer
    RoundTransitionDirective.PassToNextSeat -> RoundTransitionDirectivePersistenceDto.PassToNextSeat
    is RoundTransitionDirective.PassTo -> RoundTransitionDirectivePersistenceDto.PassTo(nextDealerPlayerId.toString())
}

/**
 * 將 persistence DTO 還原為領域層的下一任莊家。
 *
 * @return 轉換後的結果。
 */
fun RoundTransitionDirectivePersistenceDto.toDomain(): RoundTransitionDirective = when (this) {
    RoundTransitionDirectivePersistenceDto.RepeatDealer -> RoundTransitionDirective.RepeatDealer
    RoundTransitionDirectivePersistenceDto.PassToNextSeat -> RoundTransitionDirective.PassToNextSeat
    is RoundTransitionDirectivePersistenceDto.PassTo -> RoundTransitionDirective.PassTo(Uuid.parse(nextDealerPlayerId))
}
