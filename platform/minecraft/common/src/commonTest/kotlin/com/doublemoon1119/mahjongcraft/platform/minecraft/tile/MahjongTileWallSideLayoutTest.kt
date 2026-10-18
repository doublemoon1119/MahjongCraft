package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideArrangement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideReference
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證邏輯牌牆面配置如何對應到 Minecraft 桌子的四個標準側面。 */
class MahjongTileWallSideLayoutTest {
    /** 恢復時重新建立的相同桌邊配置仍具有相同值與雜湊。 */
    @Test
    fun `equivalent resolved layouts have value equality`() {
        val arrangement = TileWallSideArrangement(TileWallSideReference.TABLE, listOf(0, 2))
        val first = MahjongTileWallSideLayout.resolve(arrangement, MahjongTableSide.SOUTH)
        val restored = MahjongTileWallSideLayout.resolve(arrangement, MahjongTableSide.NORTH)

        assertEquals(first, restored)
        assertEquals(first.hashCode(), restored.hashCode())
    }

    /** 驗證 TABLE 基準會直接使用規則提供的側面配置。 */
    @Test
    fun `table reference keeps the configured sides`() {
        val arrangement = TileWallSideArrangement(
            reference = TileWallSideReference.TABLE,
            quarterTurns = listOf(0, 2),
        )

        val layout = MahjongTileWallSideLayout.resolve(arrangement, MahjongTableSide.NORTH)

        assertEquals(2, layout.sideCount)
        assertEquals(MahjongTableSide.SOUTH, layout.sideOf(0))
        assertEquals(MahjongTableSide.NORTH, layout.sideOf(1))
    }

    /** 驗證 DEALER 基準會以莊家所在側旋轉配置，而不改變邏輯牌牆順序。 */
    @Test
    fun `dealer reference rotates around every dealer side`() {
        val arrangement = TileWallSideArrangement(
            reference = TileWallSideReference.DEALER,
            quarterTurns = listOf(0, 2),
        )

        MahjongTableSide.entries.forEach { dealerSide ->
            val layout = MahjongTileWallSideLayout.resolve(arrangement, dealerSide)

            assertEquals(2, layout.sideCount)
            assertEquals(dealerSide, layout.sideOf(0))
            assertEquals(opposite(dealerSide), layout.sideOf(1))
        }
    }

    /** 驗證標準四面牌牆的莊家基準配置維持既有方向順序。 */
    @Test
    fun `four side dealer arrangement preserves standard order`() {
        val arrangement = TileWallSideArrangement(
            reference = TileWallSideReference.DEALER,
            quarterTurns = listOf(0, 3, 2, 1),
        )

        val layout = MahjongTileWallSideLayout.resolve(arrangement, MahjongTableSide.SOUTH)

        assertEquals(
            listOf(
                MahjongTableSide.SOUTH,
                MahjongTableSide.WEST,
                MahjongTableSide.NORTH,
                MahjongTableSide.EAST,
            ),
            (0 until 4).map(layout::sideOf),
        )
    }

    /** 取得測試使用的相對桌邊。 */
    private fun opposite(side: MahjongTableSide): MahjongTableSide = when (side) {
        MahjongTableSide.SOUTH -> MahjongTableSide.NORTH
        MahjongTableSide.WEST -> MahjongTableSide.EAST
        MahjongTableSide.NORTH -> MahjongTableSide.SOUTH
        MahjongTableSide.EAST -> MahjongTableSide.WEST
    }
}
