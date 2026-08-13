package com.doublemoon1119.mahjongcraft.platform.fabric.server.dice

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import net.minecraft.util.math.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Fabric 與共用麻將桌朝向的映射測試。 */
class FabricMahjongTableFacingTest {
    /** 四個水平朝向應保持相同語意。 */
    @Test
    fun `horizontal directions map to common facings`() {
        assertEquals(MahjongTableFacing.NORTH, Direction.NORTH.toMahjongTableFacing())
        assertEquals(MahjongTableFacing.EAST, Direction.EAST.toMahjongTableFacing())
        assertEquals(MahjongTableFacing.SOUTH, Direction.SOUTH.toMahjongTableFacing())
        assertEquals(MahjongTableFacing.WEST, Direction.WEST.toMahjongTableFacing())
    }

    /** 垂直方向不得被誤用為桌子水平朝向。 */
    @Test
    fun `vertical directions are rejected`() {
        assertFailsWith<IllegalStateException> { Direction.UP.toMahjongTableFacing() }
        assertFailsWith<IllegalStateException> { Direction.DOWN.toMahjongTableFacing() }
    }
}
