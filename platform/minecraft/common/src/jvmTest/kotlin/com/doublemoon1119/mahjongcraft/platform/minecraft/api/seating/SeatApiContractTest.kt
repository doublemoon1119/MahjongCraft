package com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatPlacement
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證公開座位 API 的資料契約、識別範圍與 Java 可見性。 */
class SeatApiContractTest {
    /** 非有限幾何值不可進入座位契約。 */
    @Test
    fun `seat geometry rejects non finite coordinates and yaw`() {
        val invalidValues = listOf(
            { SeatGeometry(Double.NaN, 0.0, 0.0, 0.0f) },
            { SeatGeometry(0.0, Double.POSITIVE_INFINITY, 0.0, 0.0f) },
            { SeatGeometry(0.0, 0.0, Double.NEGATIVE_INFINITY, 0.0f) },
            { SeatGeometry(0.0, 0.0, 0.0, Float.NaN) },
            { SeatGeometry(0.0, 0.0, 0.0, Float.POSITIVE_INFINITY) },
        )

        invalidValues.forEach { create ->
            assertFailsWith<IllegalArgumentException>(block = create)
        }
    }

    /** 合法幾何值保留提供者回報的精確位置與朝向。 */
    @Test
    fun `seat geometry preserves finite values`() {
        val geometry = SeatGeometry(1.25, 64.0, -2.5, 180.0f)

        assertEquals(1.25, geometry.x)
        assertEquals(64.0, geometry.y)
        assertEquals(-2.5, geometry.z)
        assertEquals(180.0f, geometry.yaw)
    }

    /** 方塊、實體及多座位目標必須以不同且可比對的身分表示。 */
    @Test
    fun `seat identities distinguish source and seat slot`() {
        val block = SeatIdentity("minecraft:overworld", "minecraft:block", "10,64,-2")
        val entity = SeatIdentity("minecraft:overworld", "minecraft:entity", "00000000-0000-0000-0000-000000000001")
        val firstEntitySlot = entity.copy(slot = "left")
        val secondEntitySlot = entity.copy(slot = "right")

        assertNotEquals(block, entity)
        assertNotEquals(firstEntitySlot, secondEntitySlot)
        assertEquals("left", firstEntitySlot.slot)
        assertEquals("right", secondEntitySlot.slot)
    }

    /** 公開座位資料與 provider 介面提供 Java 可反射取得的型別與建構子。 */
    @Test
    fun `seat api exposes java usable declarations`() {
        assertTrue(SeatProvider::class.java.isInterface)
        assertTrue(SeatTarget::class.java.isInterface)
        assertTrue(SeatProvider::class.java.declaredMethods.any { it.name == "resolve" })
        assertTrue(SeatTarget::class.java.declaredMethods.any { it.name == "inspect" })

        val geometryConstructor = SeatGeometry::class.java.getConstructor(
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        )
        assertTrue(Modifier.isPublic(geometryConstructor.modifiers))
        val identityConstructor = SeatIdentity::class.java.getConstructor(
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
        )
        assertTrue(Modifier.isPublic(identityConstructor.modifiers))
    }

    /** 固定席位請求可由第三方以穩定桌子與席位資訊建立。 */
    @Test
    fun `seat request retains fixed placement`() {
        val tableId = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val placement = MahjongSeatPlacement(1.5, 64.0, -2.5, 180.0f)
        val request = SeatRequest(tableId, 2, "minecraft:overworld", placement)

        assertEquals(tableId, request.tableId)
        assertEquals(2, request.seatIndex)
        assertEquals(placement, request.placement)
    }
}
