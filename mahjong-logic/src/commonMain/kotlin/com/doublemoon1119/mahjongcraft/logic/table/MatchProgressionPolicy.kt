package com.doublemoon1119.mahjongcraft.logic.table

/** 由規則決定初始局位與每局結算後整場去向的純邏輯 policy。 */
interface MatchProgressionPolicy {
    /** 建立此規則的初始局位。 */
    fun initialPosition(playerCount: Int): MatchRoundPosition = MatchRoundPosition.initial()

    /** 依本局權威結果決定終局、連莊或下一局位。 */
    fun decide(context: MatchProgressionContext): MatchProgressionDecision
}
