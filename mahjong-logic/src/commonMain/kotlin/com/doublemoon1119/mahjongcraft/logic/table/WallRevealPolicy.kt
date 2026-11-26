package com.doublemoon1119.mahjongcraft.logic.table

/** 由規則決定指定流程節點是否公開或取消等待中的牌牆資訊。 */
fun interface WallRevealPolicy {
    /** 根據 [context] 建立不可變的牌牆公開決策。 */
    fun resolve(context: WallRevealContext): WallRevealDecision
}
