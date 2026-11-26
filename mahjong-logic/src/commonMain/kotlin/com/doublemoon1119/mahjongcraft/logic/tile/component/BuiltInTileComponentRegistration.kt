package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentEffectDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet

/**
 * 登記所有規則共用的牌張組件：牌面授權、姿態、外觀與透明牌，以及透明牌的效果與投影。
 *
 * 規則只要在建牌時為牌加上 [BuiltInTileComponents.TRANSPARENT] 就能使用透明牌。
 *
 * - 透明牌在摸牌序列與保留牌中和一般牌一樣不公開，避免預先知道之後摸到的牌。
 * - 摸進手牌、成為副露、打出或移出後，牌面對所有人公開。
 * - 知道牌面的讀取者同時得知它是透明牌。
 *
 * @param registry 本次執行尚未凍結的組件登記。
 */
fun registerBuiltInTileComponents(registry: ComponentRegistrar) {
    registry.register(BuiltInTileComponents.FACE_ACCESS)
    registry.register(BuiltInTileComponents.POSE)
    registry.register(BuiltInTileComponents.APPEARANCE)
    registry.register(BuiltInTileComponents.TRANSPARENT)
    registry.registerView(BuiltInTileComponentViews.TRANSPARENT)
    registry.registerEffect(
        definition = ComponentEffectDefinition(
            id = "mahjongcraft:tile/effect/transparent",
            ownerType = BuiltInComponentOwners.TILE,
            appliesTo = { _, _ -> true },
            resolve = { context ->
                val tile = context.owner
                if (tile.components[BuiltInTileComponents.TRANSPARENT] == null) return@ComponentEffectDefinition ComponentSet.EMPTY
                val undrawn = context.tableState.tileWall.getAllTiles().any { it.id == tile.id } ||
                    context.tableState.reservedWallTiles.any { it.id == tile.id }
                if (!undrawn) {
                    ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public)
                } else {
                    ComponentSet.EMPTY
                }
            },
        ),
    )
    registry.registerProjection(
        definition = ComponentProjectionDefinition(
            id = "mahjongcraft:tile/projection/transparent",
            ownerType = BuiltInComponentOwners.TILE,
            component = BuiltInTileComponents.TRANSPARENT,
            viewType = BuiltInTileComponentViews.TRANSPARENT,
            appliesTo = { _, _ -> true },
            project = { _, _ -> Unit },
        ),
    )
}
