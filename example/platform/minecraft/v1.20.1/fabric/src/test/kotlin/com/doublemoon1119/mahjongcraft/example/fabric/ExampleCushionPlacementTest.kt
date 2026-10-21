package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleCushionPlacement
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleFurnitureSeatRules
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 檢查坐墊的格線、表面高度、四向朝向與空位拆除規則。 */
class ExampleCushionPlacementTest {
    /** 水平位置取命中方塊中央，高度保留半磚與薄表面的實際頂面。 */
    @Test
    fun `placement keeps fractional surface height and centers the clicked block`() {
        listOf(10.5, 10.0625, 11.0).forEach { surfaceY ->
            val position = ExampleCushionPlacement.positionAt(BlockPos(-2, 10, 3), surfaceY)
            assertEquals(-1.5, position.x)
            assertEquals(3.5, position.z)
            assertEquals(surfaceY, position.y)
        }
    }

    /** 放置時只產生四個正方向，負角度與整圈角度也使用同一規則。 */
    @Test
    fun `placement rotation snaps to quarter turns`() {
        mapOf(
            0.0f to 0.0f,
            44.0f to 0.0f,
            45.0f to 90.0f,
            135.0f to 180.0f,
            225.0f to 270.0f,
            315.0f to 0.0f,
            -90.0f to 270.0f,
            450.0f to 90.0f,
        ).forEach { (input, expected) -> assertEquals(expected, ExampleCushionPlacement.snapYaw(input)) }
    }

    /** 半磚、完整方塊與階梯分段都能以相接的頂面支撐坐墊。 */
    @Test
    fun `support follows actual top surfaces instead of full cube height`() {
        val cushion = Box(0.125, 0.5, 0.125, 0.875, 0.7, 0.875)
        assertTrue(ExampleCushionPlacement.hasSurfaceSupport(cushion, listOf(Box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0))))
        assertTrue(ExampleCushionPlacement.hasSurfaceSupport(cushion, listOf(Box(0.0, 0.0, 0.0, 1.0, 0.5, 0.5))))
        assertTrue(ExampleCushionPlacement.hasSurfaceSupport(cushion.offset(0.0, 0.5, 0.0), listOf(Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))))
    }

    /** 支撐移除、只貼側邊或高度不相接，都不能繼續當成有效支撐。 */
    @Test
    fun `removed and side only surfaces do not support a cushion`() {
        val cushion = Box(0.125, 0.5, 0.125, 0.875, 0.7, 0.875)
        assertFalse(ExampleCushionPlacement.hasSurfaceSupport(cushion, emptyList()))
        assertFalse(ExampleCushionPlacement.hasSurfaceSupport(cushion, listOf(Box(0.875, 0.0, 0.0, 1.0, 0.5, 1.0))))
        assertFalse(ExampleCushionPlacement.hasSurfaceSupport(cushion, listOf(Box(0.0, 0.0, 0.0, 1.0, 0.4, 1.0))))
    }

    /** 玩家只能拆除有修改權限的空坐墊，重複攻擊不會重複取得物品。 */
    @Test
    fun `breaking requires a present empty cushion and modification permission`() {
        assertTrue(ExampleFurnitureSeatRules.canBreakCushion(removed = false, occupied = false, canModify = true))
        assertFalse(ExampleFurnitureSeatRules.canBreakCushion(removed = true, occupied = false, canModify = true))
        assertFalse(ExampleFurnitureSeatRules.canBreakCushion(removed = false, occupied = true, canModify = true))
        assertFalse(ExampleFurnitureSeatRules.canBreakCushion(removed = false, occupied = false, canModify = false))
    }
}
