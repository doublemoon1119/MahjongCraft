package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import kotlinx.serialization.Serializable

/** 本局莊家推進決策的持久化類型。 */
@Serializable
enum class RoundTransitionDirectivePersistenceDto {
    /** 莊家連莊。 */
    REPEAT_DEALER,

    /** 莊家過莊。 */
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
