package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * 讓既有組件的生命週期也適用於其他規則，不修改原本的登記。
 *
 * 例如另一套規則沿用日麻的立直宣告標記時，由該規則的擴充登記這份範圍。適用時，原生命週期的初值、
 * 必要值與換局處理一併套用；任一登記判定適用即可保存此組件。投影仍由各自的投影定義決定。
 *
 * @property id 這份範圍登記的完整識別碼。
 * @property component 已登記生命週期的組件。
 * @property appliesTo 判斷額外適用的規則及設定。
 */
class ComponentLifecycleScope(
    val id: String,
    val component: ComponentType<*>,
    val appliesTo: (String, MahjongRuleConfig) -> Boolean,
) {
    init {
        NamespacedId.requireValid(id) { "Component lifecycle scope ID must be namespaced: $id" }
    }
}
