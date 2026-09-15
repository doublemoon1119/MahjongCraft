package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugVirtualTableLayoutFactory.Companion.VIRTUAL_CONTROLLER_FORWARD_BLOCKS
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** 驗證 debug 虛擬桌的 controller 推算與座位 0 預設格位。 */
class DebugVirtualTableLayoutFactoryTest {
    private val factory = DebugVirtualTableLayoutFactory()

    /** 虛擬 controller 沿玩家朝向前推固定距離，高度與玩家腳下方塊相同。 */
    @Test
    fun `pushes the controller along the player facing`() {
        val expectedOffsets = mapOf(
            MahjongTableFacing.NORTH to (0 to -VIRTUAL_CONTROLLER_FORWARD_BLOCKS),
            MahjongTableFacing.EAST to (VIRTUAL_CONTROLLER_FORWARD_BLOCKS to 0),
            MahjongTableFacing.SOUTH to (0 to VIRTUAL_CONTROLLER_FORWARD_BLOCKS),
            MahjongTableFacing.WEST to (-VIRTUAL_CONTROLLER_FORWARD_BLOCKS to 0),
        )

        expectedOffsets.forEach { (facing, offsets) ->
            val (offsetX, offsetZ) = offsets
            val layout = factory.create(
                playerBlockX = 100,
                playerBlockY = 64,
                playerBlockZ = -40,
                tableFacing = facing,
            )

            assertEquals(100 + offsetX, layout.controllerX, "controller x for facing $facing")
            assertEquals(64, layout.controllerY, "controller y for facing $facing")
            assertEquals(-40 + offsetZ, layout.controllerZ, "controller z for facing $facing")
            assertEquals(facing, layout.tableFacing)
        }
    }

    /** 省略座位的手牌格位等同明確指定近側座位。 */
    @Test
    fun `defaults hand placement to the near seat`() {
        val layout = factory.create(0, 64, 0, MahjongTableFacing.NORTH)

        assertEquals(
            layout.handPlacement(
                seatIndex = DebugVirtualTableLayout.DEBUG_SEAT_INDEX,
                handSize = 13,
                tileIndex = 3,
            ),
            layout.handPlacement(handSize = 13, tileIndex = 3),
        )
    }

    /** 手牌格位沿牌列前進，相鄰格位不會重疊。 */
    @Test
    fun `spaces adjacent hand tiles apart`() {
        val layout = factory.create(0, 64, 0, MahjongTableFacing.NORTH)

        assertNotEquals(
            layout.handPlacement(handSize = 13, tileIndex = 0),
            layout.handPlacement(handSize = 13, tileIndex = 1),
        )
    }

    /** 單組副露的格位數量等於該副露的牌數，且彼此不重疊。 */
    @Test
    fun `builds one placement per meld tile`() {
        val layout = factory.create(0, 64, 0, MahjongTableFacing.SOUTH)

        val placements = layout.meldPlacements(type = MeldType.OPEN_KAN, tileCount = 4)

        assertEquals(4, placements.size)
        assertEquals(4, placements.distinct().size)
    }
}
