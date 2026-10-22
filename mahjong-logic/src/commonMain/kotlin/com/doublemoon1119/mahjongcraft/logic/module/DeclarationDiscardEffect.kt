package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer

/**
 * 一次宣告及捨牌允許改動的規則資料。
 *
 * Flow 仍會檢查結果只改動本次宣告授權的欄位，以及牌張和分數沒有違反規則；回傳物件不代表已經寫入。
 *
 * @property player 宣告及捨牌後的玩家狀態。
 * @property dynamicRuleState 宣告後完整的規則動態狀態；維持原狀時傳入原值，null 明確表示清除，不代表省略更新。
 * @property cost 本次宣告應支付的點數，必須為非負值。
 */
data class DeclarationDiscardEffect(
    val player: MahjongPlayer,
    val dynamicRuleState: DynamicRuleState?,
    val cost: Int = 0,
) {
    init {
        require(cost >= 0) { "Declaration cost must not be negative" }
    }
}
