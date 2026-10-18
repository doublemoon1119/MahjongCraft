package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideArrangement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideReference
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證玩家人數、牌牆面數與桌邊擺法分開後的完整座標投影。 */
class MahjongTileWallArrangementProjectionTest {
    /** 兩位莊家都能使用四面牌牆，且桌子轉向不會減少牌牆面數。 */
    @Test
    fun `two players retain four walls for both dealers and every table facing`() {
        MahjongTableFacing.entries.forEach { facing ->
            repeat(2) { dealer ->
                val context = context(facing, dealer, TileWallSideReference.DEALER, listOf(0, 3, 2, 1))
                val placements = (0 until context.sideCount).map { side ->
                    context.project(TileWallPlacement(TileWallPosition(side, 8, 0)))
                }
                assertEquals(4, placements.map { it.x to it.z }.distinct().size)
                assertEquals(4, placements.map { it.yaw }.distinct().size)
                assertEquals(
                    MahjongTileTableLayout.seatYaw(facing, MahjongTableSeatLayout.TWO_PLAYER, dealer),
                    placements.first().yaw,
                )
            }
        }
    }

    /** 固定配置換莊後保持位置，莊家基準配置則轉到相對桌邊。 */
    @Test
    fun `fixed and dealer based arrangements differ only in their anchor`() {
        listOf(listOf(0, 1), listOf(0, 2)).forEach { sides ->
            MahjongTableFacing.entries.forEach { facing ->
                val tile = TileWallPlacement(TileWallPosition(0, 8, 0))
                val fixedSouth = context(facing, 0, TileWallSideReference.TABLE, sides).project(tile)
                val fixedNorth = context(facing, 1, TileWallSideReference.TABLE, sides).project(tile)
                assertEquals(fixedSouth, fixedNorth)
                val rotated = context(facing, 1, TileWallSideReference.DEALER, sides).project(tile)
                assertEquals(1.0 - fixedSouth.x, rotated.x, 1e-9)
                assertEquals(1.0 - fixedSouth.z, rotated.z, 1e-9)
                assertEquals((fixedSouth.yaw + 180f).mod(360f), rotated.yaw)
            }
        }
    }

    /** 跨越相鄰或相對牌牆的小數格位沿外緣移動，沒有穿過桌心或產生非有限座標。 */
    @Test
    fun `fractional positions between configured walls remain outside table center`() {
        listOf(listOf(0, 1), listOf(0, 2), listOf(0, 3)).forEach { sides ->
            MahjongTableFacing.entries.forEach { facing ->
                repeat(2) { dealer ->
                    val context = context(facing, dealer, TileWallSideReference.DEALER, sides)
                    (0..20).forEach { step ->
                        val placement = context.project(
                            TileWallPlacement(
                                position = TileWallPosition(0, 16, 0),
                                offset = TileWallPlacementOffset(alongWallStacks = step / 20.0),
                            ),
                        )
                        assertTrue(placement.x.isFinite() && placement.y.isFinite() && placement.z.isFinite())
                        assertTrue(maxOf(abs(placement.x - 0.5), abs(placement.z - 0.5)) > 0.8)
                    }
                }
            }
        }
    }

    /** 每面只有一墩時，跨相對牆面的小數位移也不會產生非有限座標。 */
    @Test
    fun `single stack opposite walls retain finite fractional projections`() {
        val context = context(MahjongTableFacing.NORTH, 0, TileWallSideReference.TABLE, listOf(0, 2)).copy(stacksPerSide = 1)
        (0..20).forEach { step ->
            val placement = context.project(
                TileWallPlacement(
                    position = TileWallPosition(0, 0, 0),
                    offset = TileWallPlacementOffset(alongWallStacks = step / 20.0),
                ),
            )
            assertTrue(placement.x.isFinite() && placement.y.isFinite() && placement.z.isFinite())
        }
    }

    /**
     * 以完整的兩人座位布局建立測試牌牆，不由玩家人數推算牌牆面數。
     *
     * @param facing 桌子的世界朝向。
     * @param dealer 莊家座位索引。
     * @param reference 規則固定的方向基準。
     * @param sides 規則固定的邏輯面與四方向對應。
     * @return 用於投影的完整情境。
     */
    private fun context(
        facing: MahjongTableFacing,
        dealer: Int,
        reference: TileWallSideReference,
        sides: List<Int>,
    ): MahjongTileWallProjectionContext = MahjongTileWallProjectionContext(
        controllerX = 0,
        controllerY = 64,
        controllerZ = 0,
        tableFacing = facing,
        sideLayout = MahjongTileWallSideLayout.resolve(
            TileWallSideArrangement(reference, sides),
            MahjongTableSeatLayout.TWO_PLAYER.sideOf(dealer),
        ),
        stacksPerSide = 17,
    )
}
