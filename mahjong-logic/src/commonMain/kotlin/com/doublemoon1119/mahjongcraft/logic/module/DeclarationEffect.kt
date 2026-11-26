package com.doublemoon1119.mahjongcraft.logic.module

/**
 * 宣告一個規則擴充動作的效果。
 *
 * 宣告對和牌打點的影響不在這裡表示，而是由 [PositionRules.winValue] 的 `declarations` 參數計算，
 * 因為加番對點數的影響取決於整手牌。
 *
 * @property cost 宣告當下立即付出的點數。
 * @property locksHand 宣告後是否不能再改變手牌，只能打出摸到的牌。
 */
data class DeclarationEffect(
    val cost: Int = 0,
    val locksHand: Boolean = false,
) {
    init {
        require(cost >= 0) { "Declaration cost must not be negative" }
    }

    /** [DeclarationEffect] 的固定值。 */
    companion object {
        /** 沒有任何效果。 */
        val NONE: DeclarationEffect = DeclarationEffect()
    }
}
