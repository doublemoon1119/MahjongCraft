package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [SingleSideReservedWallTrackPlanner] 的開門面固定、連續格位與碰撞拒絕測試。 */
class SingleSideReservedWallTrackTest {
    /** 低點數開門時應將八墩軌道向面內平移，且全部保留在開門面。 */
    @Test
    fun `track remains on opening side near right edge`() {
        val track = requireNotNull(
            SingleSideReservedWallTrackPlanner.plan(
                opening = WallOpening(2, 2),
                stacksPerSide = 17,
                stackCount = 8,
                occupiedPositions = emptySet(),
            ),
        )

        assertEquals(2, track.side)
        assertEquals((7 downTo 0).toList(), track.stackIndices)
    }

    /** 台麻式八個完整牌墩可以共用同一軌道，兩層位置保持相同墩序。 */
    @Test
    fun `eight complete stacks share one continuous track`() {
        val track = requireNotNull(
            SingleSideReservedWallTrackPlanner.plan(
                opening = WallOpening(1, 18),
                stacksPerSide = 18,
                stackCount = 8,
                occupiedPositions = emptySet(),
            ),
        )

        assertEquals((17 downTo 10).toList(), track.stackIndices)
        track.stackIndices.indices.forEach { index ->
            assertEquals(track.position(index, 0).stack, track.position(index, 1).stack)
        }
    }

    /** 候選區段皆與仍在牌牆中的牌碰撞時應拒絕，而非回傳部分布局。 */
    @Test
    fun `occupied opening side rejects track`() {
        val occupied = buildSet {
            repeat(17) { stack ->
                add(TileWallPosition(0, stack, 0))
                add(TileWallPosition(0, stack, 1))
            }
        }

        assertNull(
            SingleSideReservedWallTrackPlanner.plan(
                opening = WallOpening(0, 8),
                stacksPerSide = 17,
                stackCount = 8,
                occupiedPositions = occupied,
            ),
        )
    }

    /** 後續補牌容量可以先由活牌占用，不應被誤判成開局保留牌碰撞。 */
    @Test
    fun `future transition capacity may remain occupied by live wall`() {
        val occupied = buildSet {
            listOf(1, 0).forEach { stack ->
                add(TileWallPosition(0, stack, 0))
                add(TileWallPosition(0, stack, 1))
            }
        }

        val track = requireNotNull(
            SingleSideReservedWallTrackPlanner.plan(
                opening = WallOpening(0, 2),
                stacksPerSide = 17,
                stackCount = 10,
                occupiedPositions = occupied,
                initialVacantStackCount = 8,
                extraStackAfterHead = true,
            ),
        )

        assertEquals((9 downTo 0).toList(), track.stackIndices)
    }
}
