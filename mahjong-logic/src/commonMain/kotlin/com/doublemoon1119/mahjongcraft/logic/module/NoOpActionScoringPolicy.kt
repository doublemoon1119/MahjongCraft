package com.doublemoon1119.mahjongcraft.logic.module

/** 動作成立時不收付任何點數的安全預設 policy。 */
object NoOpActionScoringPolicy : ActionScoringPolicy {
    /** 所有動作都不收付。 */
    override fun resolve(context: ActionScoringContext): ActionScoring? = null
}
