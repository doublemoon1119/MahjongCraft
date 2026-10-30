package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch

/**
 * 收取規則點數池的結果，尚未分配給玩家或寫入桌況。
 *
 * @property points 本次收取的實際點數，不能為負值。
 * @property tableComponents 收取後的桌況組件變更；未指定的資料保持原值。
 */
data class PointPotCollection(
    val points: Int,
    val tableComponents: ComponentPatch,
) {
    init {
        require(points >= 0) { "Collected pot points must not be negative" }
    }
}
