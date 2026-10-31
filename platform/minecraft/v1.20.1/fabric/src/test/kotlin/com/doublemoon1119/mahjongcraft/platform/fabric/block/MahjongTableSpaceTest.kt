package com.doublemoon1119.mahjongcraft.platform.fabric.block

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSpace
import net.minecraft.util.math.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證所有麻將桌共用的固定範圍。 */
class MahjongTableSpaceTest {
    /** 擺牌那一層是控制方塊上方一格、以其為中心的 3×3。 */
    @Test
    fun `play space is the three by three layer above the controller`() {
        val cells = MahjongTableSpace.playSpaceCells(BlockPos(10, 64, -5))

        val expected = (9..11).flatMap { x -> (-6..-4).map { z -> BlockPos(x, 65, z) } }.toSet()
        assertEquals(expected, cells.toSet())
        assertEquals(9, cells.size)
    }
}
