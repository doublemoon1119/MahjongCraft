package com.doublemoon1119.mahjongcraft.testing.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.registerTaiwanPlayerComponents
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.registerTaiwanTableComponents
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import kotlin.uuid.Uuid

/**
 * 建立只包含台灣麻將玩家與對局組件的已凍結組件登記。
 *
 * @return 測試用組件登記。
 */
fun taiwanComponentRegistryForTests(): ComponentRegistry = ComponentRegistryImpl().apply {
    BuiltInComponentOwners.ALL.forEach(::registerOwner)
    registerTaiwanPlayerComponents(this)
    registerTaiwanTableComponents(this)
    freeze()
}

/**
 * 以 [observerId] 為觀察者建立台灣麻將快照，並依正式公開範圍投影玩家組件。
 *
 * @param observerId 觀察者，必須是桌上的玩家。
 * @return 觀察者看得到的快照。
 */
fun TableState.toTaiwanSnapshotForTests(observerId: Uuid): TableStateSnapshot {
    require(players.any { it.id == observerId }) { "The test observer must belong to this match" }
    require(config is TaiwanRuleConfig) { "Taiwan snapshot fixtures require a Taiwan configuration" }
    val registry = taiwanComponentRegistryForTests()
    val snapshot = toSnapshot(setOf(observerId))
    return snapshot.copy(
        players = snapshot.players.map { player ->
            val owner = players.single { it.id == player.id }
            player.copy(
                componentViews = registry.project(
                    context = ComponentProjectionContext(
                        ruleModuleId = "test:taiwan",
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
