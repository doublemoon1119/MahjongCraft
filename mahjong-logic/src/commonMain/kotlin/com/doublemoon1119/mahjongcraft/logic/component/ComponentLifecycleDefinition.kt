package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * 一項組件的建立與換局規則，不取代局內的正式動作。
 *
 * @param O 對象型別。
 * @param V 組件值型別。
 * @property component 要管理的組件定義，也是此登記的身分。
 * @property ownerType 適用對象。
 * @property appliesTo 判斷規則及設定是否需要此組件。
 * @property required 適用時是否必須有值；缺值不能靜默補成預設。
 * @property roundTransition 換局處理方式。
 * @property initialValue 正式建立時產生初值；回傳 null 表示不建立選用值。
 * @property transform 換局轉換；只在選擇 TRANSFORM 時提供。
 */
class ComponentLifecycleDefinition<O : Any, V : Any>(
    val component: ComponentType<V>,
    val ownerType: ComponentOwnerType<O>,
    val appliesTo: (String, MahjongRuleConfig) -> Boolean,
    val required: Boolean,
    val roundTransition: ComponentRoundTransition,
    val initialValue: (ComponentLifecycleContext<O>) -> V?,
    val transform: ((ComponentLifecycleContext<O>, V) -> V?)? = null,
) {
    init {
        require(component.target === ownerType) { "Component lifecycle owner does not match component" }
        require((roundTransition == ComponentRoundTransition.TRANSFORM) == (transform != null)) {
            "Component lifecycle transform must match its round transition"
        }
    }
}
