package com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.mapTiles
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileReadContext
import com.doublemoon1119.mahjongcraft.logic.tile.component.resolveTileComponents
import kotlin.uuid.Uuid

/**
 * 建立日麻策略測試的可見快照，使用正式的牌張及玩家組件解算與投影。
 *
 * @param observerId 本局參與測試的讀取者，只公開其本人的手牌與私人組件。
 * @return 包含已授權牌面、特徵與玩家組件的測試快照。
 */
fun TableState.toRiichiSnapshotForTests(observerId: Uuid): TableStateSnapshot {
    require(players.any { it.id == observerId }) { "The test observer must belong to this match" }
    val registry = riichiComponentRegistryForTests()
    val module = when (val ruleConfig = config) {
        is RiichiRuleConfig -> RiichiRuleModule("test:riichi", ruleConfig)
        is ThreePlayerRiichiRuleConfig -> ThreePlayerRiichiRuleModule("test:riichi", ruleConfig)
        else -> error("Riichi snapshot fixtures require a riichi configuration")
    }
    val tiles = module.resolveTileComponents(this, registry).associateBy { it.area.tile.id }
    val snapshot = toSnapshot(setOf(observerId)).mapTiles { tile ->
        tile.copy(face = tiles[tile.id]?.faceViewFor(TileReadContext.Player(observerId), module.id, this, registry))
    }
    return snapshot.copy(
        componentViews = registry.project(
            context = ComponentProjectionContext(
                ruleModuleId = "test:riichi",
                tableState = this,
                ownerType = BuiltInComponentOwners.TABLE,
                owner = this,
                reader = ComponentReadContext.Player(observerId),
            ),
            components = components,
        ),
        players = snapshot.players.map { player ->
            val owner = players.single { it.id == player.id }
            player.copy(
                componentViews = registry.project(
                    context = ComponentProjectionContext(
                        ruleModuleId = "test:riichi",
                        tableState = this,
                        ownerType = BuiltInComponentOwners.PLAYER,
                        owner = owner,
                        reader = ComponentReadContext.Player(observerId),
                    ),
                    components = owner.components,
                ),
            )
        },
    )
}
