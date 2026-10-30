package com.doublemoon1119.mahjongcraft.flow.api.event

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.jvm.JvmSynthetic

/**
 * 公開事件中的一個帶單位數值。
 *
 * @property unitId 數值單位的完整 namespaced ID，例如 `mahjongcraft:han`。
 * @property amount 數值；正負意義由單位定義，事件本身不限制方向。
 */
class EventQuantity private constructor(
    val unitId: String,
    val amount: Int,
) {
    init {
        NamespacedId.requireValid(unitId) { "Event quantity unit ID must be namespaced: $unitId" }
    }

    /** 事件系統內部建立資料的入口。 */
    internal companion object {
        /**
         * 建立帶單位數值。
         *
         * @param unitId 數值單位的完整 namespaced ID。
         * @param amount 數值。
         * @return 建立的公開數值。
         */
        @JvmSynthetic
        fun create(unitId: String, amount: Int): EventQuantity = EventQuantity(unitId, amount)
    }
}
