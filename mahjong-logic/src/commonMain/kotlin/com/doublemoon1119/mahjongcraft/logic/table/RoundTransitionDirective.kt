package com.doublemoon1119.mahjongcraft.logic.table

/** 本局結算後應採用的明確莊家推進決策。 */
enum class RoundTransitionDirective {
    /** 莊家連莊。 */
    REPEAT_DEALER,

    /** 莊家過莊。 */
    ADVANCE_DEALER,
}
