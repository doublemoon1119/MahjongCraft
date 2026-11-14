package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout.RiichiWallLayout
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiWallLayout
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.layout.TaiwanWallLayout
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證內建牌牆布局回報的每面墩數，與實際排出的牌牆結構一致。 */
class TileWallLayoutSideStackCountsTest {
    /** 日麻、三人日麻與有無花牌的台麻，回報的每面墩數都等於排出的結構。 */
    @Test
    fun `built in layouts report the side sizes they lay out`() {
        val layouts = listOf(
            RiichiWallLayout(RiichiRuleConfig()) to List(4) { 17 },
            ThreePlayerRiichiWallLayout(ThreePlayerRiichiRuleConfig()) to List(3) { 18 },
            TaiwanWallLayout(TaiwanRuleConfig(useFlowerTiles = true)) to List(4) { 18 },
            TaiwanWallLayout(TaiwanRuleConfig(useFlowerTiles = false)) to List(4) { 17 },
        )

        layouts.forEach { (layout, expected) ->
            val reported = layout.sideStackCounts()
            val tiles = List(reported.sum() * LAYERS_PER_STACK) { IdentifiedTile(Uuid.random(), Tile.Honor.East) }
            val structure = layout.resolve(tiles, WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 1)).structure.values

            assertEquals(expected, reported, "$layout reports unexpected side sizes")
            assertEquals(
                expected = reported,
                actual = reported.indices.map { side -> structure.filter { it.side == side }.maxOf { it.stack } + 1 },
                message = "$layout lays out a different wall than it reports",
            )
        }
    }

    private companion object {
        /** 每墩的層數。 */
        const val LAYERS_PER_STACK: Int = 2
    }
}
