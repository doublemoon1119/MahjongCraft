package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRoundTransition

/**
 * 登記台灣麻將對局組件與每局重設的生命週期。
 *
 * @param registry 本次執行尚未凍結的組件登記。
 */
fun registerTaiwanTableComponents(registry: ComponentRegistrar) {
    registry.register(TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP)
    registry.registerLifecycle(
        ComponentLifecycleDefinition(
            component = TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP,
            ownerType = BuiltInComponentOwners.TABLE,
            appliesTo = { _, config -> config is TaiwanRuleConfig },
            required = false,
            roundTransition = ComponentRoundTransition.RESET,
            initialValue = { null },
        ),
    )
}
