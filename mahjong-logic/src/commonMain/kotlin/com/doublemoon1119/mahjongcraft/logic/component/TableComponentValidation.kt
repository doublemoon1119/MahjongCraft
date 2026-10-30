package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.tile.component.resolveTileAreas

/**
 * 在權威寫入前檢查各對象的完整組件，不執行效果、初始化或任何遊戲動作。
 *
 * @param ruleModuleId 對局採用的規則識別碼。
 * @param registry 本次執行完成登記的組件定義。
 */
fun TableState.requireRegisteredComponents(ruleModuleId: String, registry: ComponentRegistry) {
    fun <O : Any> validate(
        ownerType: ComponentOwnerType<O>,
        owner: O,
        values: ComponentSet,
    ) {
        registry.validateState(
            context = ComponentLifecycleContext(
                ruleModuleId = ruleModuleId,
                tableState = this,
                ownerType = ownerType,
                owner = owner,
                previousTableState = null,
            ),
            components = values,
        )
    }
    validate(BuiltInComponentOwners.TABLE, this, components)
    players.forEach { player ->
        validate(BuiltInComponentOwners.PLAYER, player, player.components)
        player.hand.melds.forEach { validate(BuiltInComponentOwners.MELD, it, it.components) }
        player.discardPile.entries.forEach { entry ->
            validate(BuiltInComponentOwners.DISCARD_ENTRY, entry, entry.components)
            validate(BuiltInComponentOwners.TILE, entry.tile, entry.tile.components)
        }
    }
    resolveTileAreas().forEach { area -> validate(BuiltInComponentOwners.TILE, area.tile, area.tile.components) }
}
