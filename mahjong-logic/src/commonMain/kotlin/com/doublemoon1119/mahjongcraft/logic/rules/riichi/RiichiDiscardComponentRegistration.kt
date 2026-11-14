package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRoundTransition

/**
 * 登記日麻捨牌紀錄的組件，只適用日麻系規則。
 *
 * 立直宣告標記由宣告動作寫入，新捨牌沒有初值；捨牌紀錄不跨局存在。
 *
 * @param registry 尚未凍結的組件登記。
 */
fun registerRiichiDiscardComponents(registry: ComponentRegistrar) {
    registry.register(RiichiDiscardComponents.RIICHI_DECLARED)
    registry.registerLifecycle(
        definition = ComponentLifecycleDefinition(
            component = RiichiDiscardComponents.RIICHI_DECLARED,
            ownerType = BuiltInComponentOwners.DISCARD_ENTRY,
            appliesTo = { _, config -> config is RiichiFamilyRuleConfig },
            required = false,
            roundTransition = ComponentRoundTransition.RESET,
            initialValue = { null },
        ),
    )
}
