package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * 將一項組件值轉成指定讀取者可取得的具型別資料。
 *
 * 純展示值沒有局內對象；投影若需要持有者或所在位置，應在情境沒有對象時回傳 null。
 *
 * @param O 對象型別。
 * @param V 權威值型別。
 * @param P 輸出值型別。
 * @property id 投影定義識別碼。
 * @property ownerType 權威資料所屬對象。
 * @property component 原始組件定義。
 * @property viewType 對外值定義，可以與權威值使用不同型別。
 * @property appliesTo 判斷規則及設定是否適用。
 * @property project 依讀取者產生獨立輸出；null 表示連該項的存在也不交付。
 */
class ComponentProjectionDefinition<O : Any, V : Any, P : Any>(
    val id: String,
    val ownerType: ComponentOwnerType<O>,
    val component: ComponentType<V>,
    val viewType: ComponentViewType<P>,
    val appliesTo: (String, MahjongRuleConfig) -> Boolean,
    val project: (ComponentProjectionContext<O>, V) -> P?,
) {
    init {
        NamespacedId.requireValid(id) { "Component projection ID must be namespaced: $id" }
        require(component.target === ownerType) { "Component projection owner does not match component" }
    }
}
