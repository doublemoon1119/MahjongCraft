package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.reflect.KClass

/**
 * 定義可持有組件的資料型別。
 *
 * @param O 持有組件的資料型別。
 * @property id 完整的對象識別碼。
 * @property valueClass 對象的執行期型別。
 * @property requiresLifecycle 此對象保存的每個組件是否都必須登記生命週期，以宣告適用的規則。
 */
class ComponentOwnerType<O : Any>(
    val id: String,
    val valueClass: KClass<O>,
    val requiresLifecycle: Boolean = true,
) {
    init {
        NamespacedId.requireValid(id) { "Component owner ID must be namespaced: $id" }
    }

    /**
     * 確認資料符合此對象的型別。
     *
     * @param value 要檢查的資料。
     */
    fun requireInstance(value: Any) {
        require(valueClass.isInstance(value)) { "Component owner does not match $id" }
    }
}
