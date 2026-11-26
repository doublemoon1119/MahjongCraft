package com.doublemoon1119.mahjongcraft.logic.table

/** 沒有額外牌牆公開時序需求的安全預設 policy。 */
object NoOpWallRevealPolicy : WallRevealPolicy {
    /** 所有流程節點皆維持原狀。 */
    override fun resolve(context: WallRevealContext): WallRevealDecision = WallRevealDecision.NoChange
}
