package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.block.ExampleBenchPlacement
import net.minecraft.util.math.Direction
import kotlin.test.Test
import kotlin.test.assertEquals

/** 一般放置與 AI 座位放置都使用同一套長凳開口換算。 */
class ExampleBenchPlacementTest {
    /** 任一放置視角都看到開口，不是椅背。 */
    @Test
    fun `ordinary placement faces the player in every horizontal direction`() {
        Direction.Type.HORIZONTAL.forEach { facing ->
            assertEquals(facing.opposite, ExampleBenchPlacement.facing(facing))
        }
    }

    /** 從指定乘坐角度換算放置視角後，仍回到相同開口角度。 */
    @Test
    fun `AI placement preserves the assigned seat direction`() {
        Direction.Type.HORIZONTAL.forEach { facing ->
            val placement = Direction.fromRotation(ExampleBenchPlacement.playerYaw(facing.asRotation()).toDouble())
            assertEquals(facing, ExampleBenchPlacement.facing(placement))
        }
    }
}
