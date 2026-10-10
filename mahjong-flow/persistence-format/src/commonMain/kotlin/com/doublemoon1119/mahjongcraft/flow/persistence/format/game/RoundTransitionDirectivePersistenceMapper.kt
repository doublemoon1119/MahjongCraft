package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective

/**
 * 將領域層的莊家推進決策轉為 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun RoundTransitionDirective.toPersistenceDto(): RoundTransitionDirectivePersistenceDto = when (this) {
    RoundTransitionDirective.REPEAT_DEALER -> RoundTransitionDirectivePersistenceDto.REPEAT_DEALER
    RoundTransitionDirective.ADVANCE_DEALER -> RoundTransitionDirectivePersistenceDto.ADVANCE_DEALER
}

/**
 * 將 persistence DTO 還原為領域層莊家推進決策。
 *
 * @return 轉換後的結果。
 */
fun RoundTransitionDirectivePersistenceDto.toDomain(): RoundTransitionDirective = when (this) {
    RoundTransitionDirectivePersistenceDto.REPEAT_DEALER -> RoundTransitionDirective.REPEAT_DEALER
    RoundTransitionDirectivePersistenceDto.ADVANCE_DEALER -> RoundTransitionDirective.ADVANCE_DEALER
}
