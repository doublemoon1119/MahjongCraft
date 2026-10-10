package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 胡牌結算模板可讀取的完整 namespaced 欄位識別碼。
 *
 * @property value 欄位的完整 Namespaced ID。
 */
@JvmInline
value class PresentationFieldId(
    val value: String,
) {
    init {
        NamespacedId.requireValid(value) { "Presentation field ID must be namespaced: $value" }
    }

    override fun toString(): String = value
}
