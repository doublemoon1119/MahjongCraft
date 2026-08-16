package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/** [MahjongTileTableLayout] 的墩位間距、四面旋轉、角落不重疊與輸入驗證測試。 */
class MahjongTileTableLayoutTest {
    /** 同一面內不同墩應等距排列，`stack` 0 在最大 X、遞增時往負向移動；整條線額外偏移半個牌高
     *  （見 [MahjongTileTableLayout] 角落互相接角的說明），不是置中於側面正中央。 */
    @Test
    fun `stacks are evenly spaced and shifted for corner interlock`() {
        val first = wallPlacement(position = TileWallPosition(side = 0, stack = 0, layer = 0))
        val last = wallPlacement(position = TileWallPosition(side = 0, stack = STACKS_PER_SIDE - 1, layer = 0))
        val middle = wallPlacement(position = TileWallPosition(side = 0, stack = (STACKS_PER_SIDE - 1) / 2, layer = 0))
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val cornerInterlockShift = MahjongTileDimensions.TILE_HEIGHT / 2.0 +
            MahjongTileDimensions.TILE_WIDTH * CORNER_GAP_RATIO_MIRROR

        assertEquals((STACKS_PER_SIDE - 1) * stackStep, first.x - last.x, ABSOLUTE_TOLERANCE)
        assertEquals(cornerInterlockShift, middle.x - CONTROLLER_CENTER_X, ABSOLUTE_TOLERANCE)
        assertEquals(first.z, last.z, ABSOLUTE_TOLERANCE)
    }

    /**
     * 四面牌牆的所有墩（含四個角落）躺平後的世界footprint 彼此都不該重疊——這正是使用者實際在遊戲內
     * 發現過的 bug（角落偏移量只顧到端點座標重合，沒考慮牌本身的寬度）。footprint 沿墩排列方向的
     * 半寬是 [MahjongTileDimensions.TILE_WIDTH]／2，垂直於側面方向的半寬是
     * [MahjongTileDimensions.TILE_HEIGHT]／2（牌立起時的高度，躺平後變成水平方向的寬度），依 yaw
     * 判斷這張牌的兩個方向何者對應世界 X／Z。允許邊界剛好貼齊（觸碰但不重疊），只有兩個 footprint
     * 各自的 X 範圍與 Z 範圍都有正的重疊時才視為真正碰撞。
     */
    @Test
    fun `wall corners interlock without overlapping across sides`() {
        val footprints = (0 until SIDE_COUNT).flatMap { side ->
            (0 until STACKS_PER_SIDE).map { stack ->
                wallPlacement(position = TileWallPosition(side = side, stack = stack, layer = 0)).toFootprint()
            }
        }

        for (i in footprints.indices) {
            for (j in i + 1 until footprints.size) {
                assertFalse(footprints[i].overlaps(footprints[j]), "Tile footprints $i and $j overlap: ${footprints[i]} vs ${footprints[j]}")
            }
        }
    }

    /** 上層應比下層高出一個牌深，水平位置不變。 */
    @Test
    fun `upper layer sits one tile depth above lower layer`() {
        val lower = wallPlacement(position = TileWallPosition(side = 0, stack = 0, layer = 0))
        val upper = wallPlacement(position = TileWallPosition(side = 0, stack = 0, layer = 1))

        assertEquals(lower.x, upper.x, ABSOLUTE_TOLERANCE)
        assertEquals(lower.z, upper.z, ABSOLUTE_TOLERANCE)
        val expectedLayerHeight = MahjongTileDimensions.TILE_DEPTH + MahjongTileDimensions.TILE_SMALL_PADDING
        assertEquals(expectedLayerHeight, upper.y - lower.y, ABSOLUTE_TOLERANCE)
    }

    /** 改變 [TileWallPosition.side] 應繞莊家局部南側旋轉 90 度一步。 */
    @Test
    fun `side rotates around dealer local south`() {
        val south = wallPlacement(position = TileWallPosition(side = 0, stack = 0, layer = 0))
        val west = wallPlacement(position = TileWallPosition(side = 1, stack = 0, layer = 0))

        assertEquals(-(south.z - CONTROLLER_CENTER_Z), west.x - CONTROLLER_CENTER_X, ABSOLUTE_TOLERANCE)
        assertEquals(south.x - CONTROLLER_CENTER_X, west.z - CONTROLLER_CENTER_Z, ABSOLUTE_TOLERANCE)
    }

    /** 桌子朝東時，局部座標還應再旋轉至世界東向。 */
    @Test
    fun `table facing rotates local placement into world`() {
        val northFacing = wallPlacement(tableFacing = MahjongTableFacing.NORTH)
        val eastFacing = wallPlacement(tableFacing = MahjongTableFacing.EAST)
        val northOffsetX = northFacing.x - CONTROLLER_CENTER_X
        val northOffsetZ = northFacing.z - CONTROLLER_CENTER_Z

        assertEquals(-northOffsetZ, eastFacing.x - CONTROLLER_CENTER_X, ABSOLUTE_TOLERANCE)
        assertEquals(northOffsetX, eastFacing.z - CONTROLLER_CENTER_Z, ABSOLUTE_TOLERANCE)
        assertNotEquals(northFacing.yaw, eastFacing.yaw)
    }

    /** 莊家不同座位時，同一個 [TileWallPosition] 應落在不同的世界局部側面。 */
    @Test
    fun `dealer seat index rotates wall side`() {
        val dealerZero = wallPlacement(dealerSeatIndex = 0)
        val dealerOne = wallPlacement(dealerSeatIndex = 1)

        assertNotEquals(dealerZero.x to dealerZero.z, dealerOne.x to dealerOne.z)
    }

    /** 超出墩數或層數範圍應直接拒絕，不產生不合理座標。 */
    @Test
    fun `layout rejects out of range stack or layer`() {
        assertFailsWith<IllegalArgumentException> {
            wallPlacement(position = TileWallPosition(side = 0, stack = STACKS_PER_SIDE, layer = 0))
        }
        assertFailsWith<IllegalArgumentException> {
            wallPlacement(position = TileWallPosition(side = 0, stack = 0, layer = 2))
        }
    }

    /** 建立固定 controller 與可覆寫輸入的測試 placement。 */
    private fun wallPlacement(
        tableFacing: MahjongTableFacing = MahjongTableFacing.NORTH,
        dealerSeatIndex: Int = 0,
        position: TileWallPosition = TileWallPosition(side = 0, stack = 0, layer = 0),
    ): MahjongTileWallPlacement = MahjongTileTableLayout.wallPlacement(
        controllerX = 10,
        controllerY = 64,
        controllerZ = -4,
        tableFacing = tableFacing,
        dealerSeatIndex = dealerSeatIndex,
        stacksPerSide = STACKS_PER_SIDE,
        position = position,
    )

    /** 一張躺平牌的世界水平 footprint，用來檢查不同墩是否重疊。 */
    private data class Footprint(val minX: Double, val maxX: Double, val minZ: Double, val maxZ: Double)

    /** 依 yaw 判斷躺平牌的沿墩排列方向與垂直方向何者對應世界 X／Z，算出其 footprint。 */
    private fun MahjongTileWallPlacement.toFootprint(): Footprint {
        val alongHalf = MahjongTileDimensions.TILE_WIDTH / 2.0
        val perpendicularHalf = MahjongTileDimensions.TILE_HEIGHT / 2.0
        val normalizedYaw = ((yaw % FULL_YAW_DEGREES) + FULL_YAW_DEGREES) % FULL_YAW_DEGREES
        val (halfX, halfZ) = if (normalizedYaw == 0.0f || normalizedYaw == 180.0f) {
            alongHalf to perpendicularHalf
        } else {
            perpendicularHalf to alongHalf
        }
        return Footprint(minX = x - halfX, maxX = x + halfX, minZ = z - halfZ, maxZ = z + halfZ)
    }

    /** 只有 X 範圍與 Z 範圍都有正重疊時才視為碰撞；剛好貼齊的邊界不算碰撞。 */
    private fun Footprint.overlaps(other: Footprint): Boolean {
        val overlapX = minOf(maxX, other.maxX) - maxOf(minX, other.minX)
        val overlapZ = minOf(maxZ, other.maxZ) - maxOf(minZ, other.minZ)
        return overlapX > ABSOLUTE_TOLERANCE && overlapZ > ABSOLUTE_TOLERANCE
    }

    /** 固定 controller 中心、每面墩數與浮點容許誤差。 */
    private companion object {
        const val STACKS_PER_SIDE: Int = 17
        const val SIDE_COUNT: Int = 4
        const val CONTROLLER_CENTER_X: Double = 10.5
        const val CONTROLLER_CENTER_Z: Double = -3.5
        const val ABSOLUTE_TOLERANCE: Double = 1e-9
        const val FULL_YAW_DEGREES: Float = 360.0f

        /** 對應 [MahjongTileTableLayout] 私有的 `CORNER_GAP_RATIO`；改動生產端數值時記得同步這裡。 */
        const val CORNER_GAP_RATIO_MIRROR: Double = 0.25
    }
}
