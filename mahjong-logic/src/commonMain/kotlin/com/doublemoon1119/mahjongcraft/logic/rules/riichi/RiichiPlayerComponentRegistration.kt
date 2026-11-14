package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRoundTransition

/**
 * 登記日麻玩家組件的生命週期與公開範圍。
 *
 * @param registry 本次執行尚未凍結的組件登記。
 */
fun registerRiichiPlayerComponents(registry: ComponentRegistrar) {
    registry.register(RiichiPlayerComponents.DECLARATION)
    registry.registerView(RiichiPlayerComponentViews.DECLARATION)
    registry.registerLifecycle(
        definition = ComponentLifecycleDefinition(
            component = RiichiPlayerComponents.DECLARATION,
            ownerType = BuiltInComponentOwners.PLAYER,
            appliesTo = { _, config -> config is RiichiFamilyRuleConfig },
            required = false,
            roundTransition = ComponentRoundTransition.RESET,
            initialValue = { null },
        ),
    )
    registry.registerProjection(
        definition = ComponentProjectionDefinition(
            id = "mahjongcraft:riichi/projection/declaration",
            ownerType = BuiltInComponentOwners.PLAYER,
            component = RiichiPlayerComponents.DECLARATION,
            viewType = RiichiPlayerComponentViews.DECLARATION,
            appliesTo = { _, config -> config is RiichiFamilyRuleConfig },
            project = { _, value -> value },
        ),
    )
    registry.register(RiichiPlayerComponents.PERMANENT_FURITEN)
    registry.registerView(RiichiPlayerComponentViews.PERMANENT_FURITEN)
    registry.registerLifecycle(
        definition = ComponentLifecycleDefinition(
            component = RiichiPlayerComponents.PERMANENT_FURITEN,
            ownerType = BuiltInComponentOwners.PLAYER,
            appliesTo = { _, config -> config is RiichiFamilyRuleConfig },
            required = false,
            roundTransition = ComponentRoundTransition.RESET,
            initialValue = { null },
        ),
    )
    registry.registerProjection(
        definition = ComponentProjectionDefinition(
            id = "mahjongcraft:riichi/projection/permanent_furiten",
            ownerType = BuiltInComponentOwners.PLAYER,
            component = RiichiPlayerComponents.PERMANENT_FURITEN,
            viewType = RiichiPlayerComponentViews.PERMANENT_FURITEN,
            appliesTo = { _, config -> config is RiichiFamilyRuleConfig },
            project = { context, _ ->
                val owner = context.owner
                Unit.takeIf { owner != null && (context.reader as? ComponentReadContext.Player)?.playerId == owner.id }
            },
        ),
    )
    registry.register(RiichiPlayerComponents.PAO_LIABILITY)
    registry.registerView(RiichiPlayerComponentViews.PAO_LIABILITY)
    registry.registerLifecycle(
        definition = ComponentLifecycleDefinition(
            component = RiichiPlayerComponents.PAO_LIABILITY,
            ownerType = BuiltInComponentOwners.PLAYER,
            appliesTo = { _, config -> config is RiichiFamilyRuleConfig },
            required = false,
            roundTransition = ComponentRoundTransition.RESET,
            initialValue = { null },
        ),
    )
    registry.registerProjection(
        definition = ComponentProjectionDefinition(
            id = "mahjongcraft:riichi/projection/pao_liability",
            ownerType = BuiltInComponentOwners.PLAYER,
            component = RiichiPlayerComponents.PAO_LIABILITY,
            viewType = RiichiPlayerComponentViews.PAO_LIABILITY,
            appliesTo = { _, config -> config is RiichiFamilyRuleConfig },
            project = { _, value -> value },
        ),
    )
}
