package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentEffectDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRoundTransition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import kotlin.random.Random

/**
 * 登記範例組件的建立、換局、攤牌與公開牌面效果；加分牌與攤牌狀態只供伺服器使用，不交付觀察者。
 *
 * @param registry 本次執行尚未凍結的組件登記。
 */
fun registerExamplePairingComponents(registry: ComponentRegistry) {
    registry.register(ExamplePairingComponents.BONUS_TILES)
    registry.register(ExamplePairingComponents.HAND_REVEALED)
    registry.register(ExamplePairingComponents.PUBLIC_FACE)
    registry.registerLifecycle(
        ComponentLifecycleDefinition(
            component = ExamplePairingComponents.BONUS_TILES,
            ownerType = BuiltInComponentOwners.TABLE,
            appliesTo = { _, config -> config is ExamplePairingRuleConfig },
            required = true,
            roundTransition = ComponentRoundTransition.RESET,
            initialValue = { context ->
                val config = context.tableState.config as ExamplePairingRuleConfig
                ExamplePairingBonusTiles(config.wallTileValues.shuffled(Random.Default).take(config.wallTileValues.size - 1).toSet())
            },
        ),
    )
    registry.registerLifecycle(
        ComponentLifecycleDefinition(
            component = ExamplePairingComponents.HAND_REVEALED,
            ownerType = BuiltInComponentOwners.PLAYER,
            appliesTo = { _, config -> config is ExamplePairingRuleConfig },
            required = false,
            roundTransition = ComponentRoundTransition.RESET,
            initialValue = { null },
        ),
    )
    registry.registerEffect(
        ComponentEffectDefinition(
            id = "${ExamplePairingIds.RULE_MODULE}/effect/revealed_hand",
            ownerType = BuiltInComponentOwners.TILE,
            appliesTo = { _, config -> config is ExamplePairingRuleConfig },
            resolve = { context ->
                val owner = context.tableState.players.firstOrNull { player -> player.hand.standingTiles.any { it.id == context.owner.id } }
                if (owner?.components?.get(ExamplePairingComponents.HAND_REVEALED) != null) {
                    ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public).with(BuiltInTileComponents.POSE, TilePose.FACE_UP)
                } else {
                    ComponentSet.EMPTY
                }
            },
        ),
    )
    registry.registerEffect(
        ComponentEffectDefinition(
            id = "${ExamplePairingIds.RULE_MODULE}/effect/public_face",
            ownerType = BuiltInComponentOwners.TILE,
            appliesTo = { _, config -> config is ExamplePairingRuleConfig },
            resolve = { context ->
                if (context.owner.components[ExamplePairingComponents.PUBLIC_FACE] != null) {
                    ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public)
                        .with(BuiltInTileComponents.APPEARANCE, ExamplePairingComponentIds.PUBLIC_FACE_APPEARANCE)
                } else {
                    ComponentSet.EMPTY
                }
            },
        ),
    )
}
