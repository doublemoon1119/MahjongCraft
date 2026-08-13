package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

/** [MahjongDiceTableLayout] 的骰子數量、variant 輪替與雙重旋轉測試。 */
class MahjongDiceTableLayoutTest {
    /** 兩顆與三顆 layout 都應位於固定桌面高度且位置互不重疊。 */
    @Test
    fun `layouts support two and three distinct dice on tabletop`() {
        listOf(2, 3).forEach { diceCount ->
            val placements = placements(diceCount = diceCount)

            assertEquals(diceCount, placements.size)
            assertEquals(List(diceCount) { 65.0 }, placements.map { it.finalPosition.y })
            assertEquals(diceCount, placements.map { it.finalPosition }.toSet().size)
            assertEquals(List(diceCount) { it * 2 }, placements.map { it.startDelayTicks })
        }
    }

    /** 不支援的骰子數量應在進入版本 adapter 前被拒絕。 */
    @Test
    fun `layout rejects unsupported dice counts`() {
        assertFailsWith<IllegalArgumentException> { placements(diceCount = 1) }
        assertFailsWith<IllegalArgumentException> { placements(diceCount = 4) }
    }

    /** 同一桌的前四次投擲應使用四組不同 layout，第五次回到第一組。 */
    @Test
    fun `table uuid defines stable cyclic variant order`() {
        val firstCycle = (0L..3L).map { sequence -> placements(rollSequence = sequence).map { it.finalPosition } }
        val repeated = placements(rollSequence = 4).map { it.finalPosition }

        assertEquals(4, firstCycle.toSet().size)
        assertEquals(firstCycle.first(), repeated)
    }

    /** 不同 UUID 可以產生不同 variant 起點或輪替方向。 */
    @Test
    fun `different table ids can produce different variant orders`() {
        val firstOrder = (0L..3L).map { sequence ->
            placements(tableId = FIRST_TABLE_ID, rollSequence = sequence).first().finalPosition
        }
        val secondOrder = (0L..3L).map { sequence ->
            placements(tableId = SECOND_TABLE_ID, rollSequence = sequence).first().finalPosition
        }

        assertNotEquals(firstOrder, secondOrder)
    }

    /** 改變局部投入側時，動畫起點方向應繞桌面旋轉。 */
    @Test
    fun `throw side rotates trajectory around local table`() {
        val south = placements(throwSide = MahjongTableSide.SOUTH).first().startOffset
        val west = placements(throwSide = MahjongTableSide.WEST).first().startOffset

        assertEquals(-south.z, west.x, ABSOLUTE_TOLERANCE)
        assertEquals(south.x, west.z, ABSOLUTE_TOLERANCE)
    }

    /** 桌子朝東時，局部座標還應再旋轉至世界東向。 */
    @Test
    fun `table facing rotates local placement into world`() {
        val northFacing = placements(tableFacing = MahjongTableFacing.NORTH).first()
        val eastFacing = placements(tableFacing = MahjongTableFacing.EAST).first()
        val northOffsetX = northFacing.finalPosition.x - CONTROLLER_CENTER_X
        val northOffsetZ = northFacing.finalPosition.z - CONTROLLER_CENTER_Z

        assertEquals(-northOffsetZ, eastFacing.finalPosition.x - CONTROLLER_CENTER_X, ABSOLUTE_TOLERANCE)
        assertEquals(northOffsetX, eastFacing.finalPosition.z - CONTROLLER_CENTER_Z, ABSOLUTE_TOLERANCE)
    }

    /** 建立固定 controller、UUID 與可覆寫輸入的測試 placements。 */
    private fun placements(
        tableId: Uuid = FIRST_TABLE_ID,
        tableFacing: MahjongTableFacing = MahjongTableFacing.NORTH,
        throwSide: MahjongTableSide = MahjongTableSide.SOUTH,
        rollSequence: Long = 0,
        diceCount: Int = 2,
    ): List<MahjongDiceTablePlacement> = MahjongDiceTableLayout.placements(
        controllerX = 10,
        controllerY = 64,
        controllerZ = -4,
        tableId = tableId,
        tableFacing = tableFacing,
        throwSide = throwSide,
        rollSequence = rollSequence,
        diceCount = diceCount,
    )

    /** 固定 UUID、controller 中心與浮點容許誤差。 */
    private companion object {
        val FIRST_TABLE_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val SECOND_TABLE_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000002")
        const val CONTROLLER_CENTER_X: Double = 10.5
        const val CONTROLLER_CENTER_Z: Double = -3.5
        const val ABSOLUTE_TOLERANCE: Double = 1e-9
    }
}
