package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.block.ExampleBenchConnections
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.block.ExampleBenchGeometry
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 長凳連接及碰撞使用同一組局部規則，不依賴整排長度或世界掃描。 */
class ExampleBenchDesignTest {
    /** 四個朝向只接左右同朝向長凳，未載入或其他家具都不連接。 */
    @Test
    fun `only left and right neighbors with identical facing connect`() {
        val facings = listOf(
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST,
        )
        facings.forEach { facing ->
            Direction.entries.forEach { side ->
                val expected = side == facing.rotateYClockwise() || side == facing.rotateYCounterclockwise()
                assertEquals(
                    expected,
                    ExampleBenchConnections.connects(
                        facing,
                        side,
                        facing,
                    ),
                )
                assertFalse(
                    ExampleBenchConnections.connects(
                        facing,
                        side,
                        null,
                    ),
                )
                assertFalse(
                    ExampleBenchConnections.connects(
                        facing,
                        side,
                        facing.opposite,
                    ),
                )
            }
        }
    }

    /** 扶手只出現在端點，四朝向都不占用乘坐者上半身空間。 */
    @Test
    fun `connected ends remove only their armrests and preserve rider space in every direction`() {
        val rider = Box(
            3.2,
            9.0,
            3.2,
            12.8,
            32.0,
            12.8,
        )
        listOf(
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST,
        ).forEach { facing ->
            val single = ExampleBenchGeometry.boxes(
                facing,
                false,
                false,
            )
            val left = ExampleBenchGeometry.boxes(
                facing,
                true,
                false,
            )
            val right = ExampleBenchGeometry.boxes(
                facing,
                false,
                true,
            )
            val middle = ExampleBenchGeometry.boxes(
                facing,
                true,
                true,
            )
            assertEquals(8, single.size)
            assertEquals(7, left.size)
            assertEquals(7, right.size)
            assertEquals(6, middle.size)
            assertTrue(single.containsAll(middle))
            assertTrue(single.none { it.intersects(rider) })
            val back = middle.single { it.minY == 9.0 && it.maxY == 20.0 }
            when (facing) {
                Direction.NORTH -> assertEquals(13.0, back.minZ)
                Direction.EAST -> assertEquals(3.0, back.maxX)
                Direction.SOUTH -> assertEquals(3.0, back.maxZ)
                Direction.WEST -> assertEquals(13.0, back.minX)
                else -> error("Unexpected vertical facing")
            }
        }
    }
}
