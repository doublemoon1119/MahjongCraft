package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleSeatGeometry
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證範例家具 provider 與 entity 共用的乘坐高度換算。 */
class ExampleSeatGeometryTest {
    /** 椅子 provider 回報的腳底位置與 entity 乘客定位使用同一高度。 */
    @Test
    fun `chair feet height is derived from its seat entity`() {
        val seatY = ExampleSeatGeometry.chairSeatY(10)

        assertEquals(
            10.0 + 0.5625 - 0.25 - 0.586 + 0.25,
            ExampleSeatGeometry.passengerFeetY(seatY),
            absoluteTolerance = 1e-9,
        )
    }

    /** 坐墊 entity 移動後，腳底位置仍只由其當前位置決定。 */
    @Test
    fun `cushion feet height follows entity position`() {
        assertEquals(4.2 + 0.2 - 0.586, ExampleSeatGeometry.cushionFeetY(4.2), absoluteTolerance = 1e-9)
    }
}
