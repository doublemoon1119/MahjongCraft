package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet

/**
 * 一次宣告及捨牌的規則效果。
 *
 * Flow 自行打出捨牌、扣除點數並記錄動作；規則只描述資料變更，碰不到手牌、牌河或分數。
 * 回傳物件不代表已經寫入，Flow 會檢查舊值、對象與適用規則。
 *
 * @property playerComponents 宣告玩家組件的變更。
 * @property discardComponents 新捨牌紀錄寫入的組件，例如日麻的立直宣告標記。
 * @property tableComponents 對局組件的變更；未指定的組件保持原值。
 * @property cost 本次宣告應支付的點數，必須為非負值。
 */
data class DeclarationDiscardEffect(
    val playerComponents: ComponentPatch,
    val discardComponents: ComponentSet,
    val tableComponents: ComponentPatch,
    val cost: Int = 0,
) {
    init {
        require(cost >= 0) { "Declaration cost must not be negative" }
    }
}
