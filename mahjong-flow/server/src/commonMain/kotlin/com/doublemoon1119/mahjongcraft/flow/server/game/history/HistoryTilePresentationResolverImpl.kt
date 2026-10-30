package com.doublemoon1119.mahjongcraft.flow.server.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTileAudience
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTilePresentation
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.winnerPlayerIds
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileReadContext
import com.doublemoon1119.mahjongcraft.logic.tile.component.resolveTileComponents
import kotlin.uuid.Uuid

/**
 * 以與觀察者快照相同的組件解算建立牌譜呈現結果。
 *
 * 特徵以公共讀取情境投影，代表牌面可讀時能公開的資料；讀取者另記在 [HistoryTilePresentation.audience]。
 *
 * @property moduleRegistry 取得對局規則模組。
 * @property componentRegistry 牌張組件與特徵投影。
 */
class HistoryTilePresentationResolverImpl(
    private val moduleRegistry: MahjongModuleRegistry,
    private val componentRegistry: ComponentRegistry,
) : HistoryTilePresentationResolver {
    override fun resolve(tableState: TableState): Map<Uuid, HistoryTilePresentation> {
        val module = moduleRegistry.getModule(tableState.config)
        val winners = tableState.winnerPlayerIds()
        return module.resolveTileComponents(tableState, componentRegistry).associate { resolved ->
            val views = componentRegistry.project(
                context = ComponentProjectionContext(
                    ruleModuleId = module.id,
                    tableState = tableState,
                    ownerType = BuiltInComponentOwners.TILE,
                    owner = resolved.area.tile,
                    reader = ComponentReadContext.Public,
                ),
                components = resolved.components,
            )
            val audience = if (resolved.canRead(TileReadContext.Public(winners))) {
                HistoryTileAudience.Public
            } else {
                HistoryTileAudience.Players(tableState.players.filter { resolved.canRead(TileReadContext.Player(it.id, winners)) }.mapTo(linkedSetOf()) { it.id })
            }
            resolved.area.tile.id to HistoryTilePresentation(
                appearanceId = resolved.components[BuiltInTileComponents.APPEARANCE],
                views = views,
                pose = resolved.pose,
                audience = audience,
            )
        }
    }
}
