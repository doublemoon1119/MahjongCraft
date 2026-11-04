package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRoundTransition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer

/**
 * 登記台灣麻將玩家組件與每局重設的生命週期。
 *
 * @param registry 本次執行尚未凍結的組件登記。
 */
fun registerTaiwanPlayerComponents(registry: ComponentRegistrar) {
    registry.register(TaiwanPlayerComponents.READY_DECLARATION)
    registry.registerLifecycle(resetEachRound(TaiwanPlayerComponents.READY_DECLARATION))
    registry.register(TaiwanPlayerComponents.PASSED_WIN)
    registry.registerLifecycle(resetEachRound(TaiwanPlayerComponents.PASSED_WIN))
}

/**
 * 建立只適用台灣麻將、每局重設為未設定的玩家組件生命週期。
 *
 * @param component 要登記的玩家組件。
 * @return 生命週期定義。
 */
private fun <T : Any> resetEachRound(component: ComponentType<T>): ComponentLifecycleDefinition<MahjongPlayer, T> = ComponentLifecycleDefinition(
    component = component,
    ownerType = BuiltInComponentOwners.PLAYER,
    appliesTo = { _, config -> config is TaiwanRuleConfig },
    required = false,
    roundTransition = ComponentRoundTransition.RESET,
    initialValue = { null },
)
