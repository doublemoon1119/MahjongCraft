package com.doublemoon1119.mahjongcraft.logic.table

/** 摸牌後不做任何自動處理的安全預設 policy。 */
object NoOpDrawFollowUpPolicy : DrawFollowUpPolicy {
    /** 每次摸牌後都不處理。 */
    override fun resolve(context: DrawFollowUpContext): DrawFollowUpDecision = DrawFollowUpDecision.None
}
