package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatCollisionPolicy
import net.minecraft.util.math.Box
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 純顯示面板的大型包圍盒不能擋住正常座位，真正的身體碰撞仍保留。 */
class SeatPlacementCollisionTest {
    /** 玩家資訊面板包圍盒涵蓋席位，但沒有身體碰撞。 */
    @Test
    fun `overlapping visual panel does not block seat placement`() {
        val panel = Box(-4.0, 64.0, -4.0, 4.0, 70.0, 4.0)
        val seat = Box(1.0, 64.0, 0.0, 2.0, 65.0, 1.0)
        assertTrue(panel.intersects(seat))
        assertFalse(SeatCollisionPolicy.blocks(removed = false, spectator = false, physical = false))
    }

    /** 玩家與有碰撞的實際物件仍阻擋座位。 */
    @Test
    fun `physical occupants still block seat placement`() {
        assertTrue(SeatCollisionPolicy.blocks(removed = false, spectator = false, physical = true))
    }

    /** 已移除與旁觀物件不阻擋。 */
    @Test
    fun `removed and spectator objects do not occupy a seat`() {
        assertFalse(SeatCollisionPolicy.blocks(removed = true, spectator = false, physical = true))
        assertFalse(SeatCollisionPolicy.blocks(removed = false, spectator = true, physical = true))
    }
}
