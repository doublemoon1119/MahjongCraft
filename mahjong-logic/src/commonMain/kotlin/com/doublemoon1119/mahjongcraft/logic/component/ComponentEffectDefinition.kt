package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * 一個擴充提供的衍生效果，只計算資料，不修改權威狀態。
 *
 * @param O 適用對象型別。
 * @property id 效果來源的完整識別碼。
 * @property ownerType 適用的對象定義。
 * @property appliesTo 判斷規則及設定是否適用，不執行 I/O 或改變狀態。
 * @property resolve 從同一版本的輸入計算效果；不得使用亂數或依賴呼叫順序。
 */
class ComponentEffectDefinition<O : Any>(
    val id: String,
    val ownerType: ComponentOwnerType<O>,
    val appliesTo: (String, MahjongRuleConfig) -> Boolean,
    val resolve: (ComponentEffectContext<O>) -> ComponentSet,
) {
    init {
        NamespacedId.requireValid(id) { "Component effect ID must be namespaced: $id" }
    }
}
