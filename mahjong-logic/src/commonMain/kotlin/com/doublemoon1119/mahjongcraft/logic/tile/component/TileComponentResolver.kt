package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentEffectContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 依當前區域、固定組件及規則效果建立共同描述，驗證後才允許對讀取者投影。
 *
 * @param tableState 權威桌況。
 * @param registry 本次執行已登記的組件定義。
 * @return 不含重複目前位置的完整牌張描述。
 */
fun MahjongRuleModule<*>.resolveTileComponents(tableState: TableState, registry: ComponentRegistry): List<ResolvedTileComponents> {
    val areas = tableState.resolveTileAreas()
    val ids = areas.mapTo(mutableSetOf()) { it.tile.id }
    val playerIds = tableState.players.mapTo(mutableSetOf()) { it.id }
    val descriptions = describeTileComponents(tableState).toList()
    require(descriptions.map { it.tileId }.distinct().size == descriptions.size) { "Duplicate tile component description" }
    require(descriptions.all { it.tileId in ids }) { "Tile description is outside current tile areas" }
    val derived = descriptions.associateBy { it.tileId }
    return areas.map { area ->
        val defaults = tileRegionComponents(tableState, area)
        val primary = derived[area.tile.id]?.components ?: ComponentSet.EMPTY
        val additional = registry.resolveEffects(
            ComponentEffectContext(
                ruleModuleId = id,
                tableState = tableState,
                ownerType = BuiltInComponentOwners.TILE,
                owner = area.tile,
            ),
        )
        val dynamic = primary.mergePeers(additional)
        listOf(defaults, area.tile.components, dynamic).forEach { registry.validate(it) }
        val merged = defaults.merge(area.tile.components).merge(dynamic)
        val access = merged[BuiltInTileComponents.FACE_ACCESS]
        require(access !is TileFaceAccess.Players || playerIds.containsAll(access.playerIds)) { "Tile access names a player outside this match" }
        val resolved = ResolvedTileComponents(area, merged, area.tile.id in tableState.publicTileEvidenceIds)
        require(resolved.pose != TilePose.FACE_UP || resolved.isPublic) { "Face-up tiles require public access" }
        resolved
    }
}
