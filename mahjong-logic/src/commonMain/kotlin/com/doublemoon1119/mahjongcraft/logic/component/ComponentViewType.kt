package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.reflect.KClass

/**
 * 定義可以對外輸出的一種值，不代表原始權威組件。
 *
 * @param V 輸出值型別。
 * @property id 完整的輸出識別碼。
 * @property valueClass 值的執行期型別。
 * @property copyValue 建立獨立值，不能保留可變集合別名。
 * @property validateValue 驗證輸出值。
 */
class ComponentViewType<V : Any>(
    val id: String,
    val valueClass: KClass<V>,
    private val copyValue: (V) -> V,
    private val validateValue: (V) -> Unit = {},
) {
    init {
        NamespacedId.requireValid(id) { "Component view ID must be namespaced: $id" }
    }

    /**
     * 驗證並複製可交付的值。
     *
     * @param value 要交付的值。
     * @return 不保留可變別名的值。
     */
    fun snapshot(value: V): V {
        require(valueClass.isInstance(value)) { "Invalid component view value: $id" }
        validateValue(value)
        return copyValue(value).also {
            require(valueClass.isInstance(it)) { "Invalid copied component view value: $id" }
            validateValue(it)
        }
    }
}
