package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRoundTransition

/**
 * 登記日麻供託的跨局保留與公開投影，以及本局補牌進度的重設；補牌進度只供伺服器使用，不交付觀察者。
 *
 * @param registry 尚未凍結的組件登記。
 */
fun registerRiichiTableComponents(registry: ComponentRegistrar) {
    registry.register(RiichiTableComponents.POT_POINTS)
    registry.registerView(RiichiTableComponentViews.POT_POINTS)
    registry.registerLifecycle(
        definition = ComponentLifecycleDefinition(
            component = RiichiTableComponents.POT_POINTS,
            ownerType = BuiltInComponentOwners.TABLE,
            appliesTo = { _, config -> config is RiichiFamilyRuleConfig },
            required = true,
            roundTransition = ComponentRoundTransition.RETAIN,
            initialValue = { 0 },
        ),
    )
    registry.registerProjection(
        definition = ComponentProjectionDefinition(
            id = "mahjongcraft:riichi/projection/pot_points",
            ownerType = BuiltInComponentOwners.TABLE,
            component = RiichiTableComponents.POT_POINTS,
            viewType = RiichiTableComponentViews.POT_POINTS,
            appliesTo = { _, config -> config is RiichiFamilyRuleConfig },
            project = { _, value -> value },
        ),
    )
    registry.register(RiichiTableComponents.WALL_PROGRESS)
    registry.registerLifecycle(
        definition = ComponentLifecycleDefinition(
            component = RiichiTableComponents.WALL_PROGRESS,
            ownerType = BuiltInComponentOwners.TABLE,
            appliesTo = { _, config -> config is RiichiFamilyRuleConfig },
            required = true,
            roundTransition = ComponentRoundTransition.RESET,
            initialValue = { RiichiWallProgress() },
        ),
    )
}
