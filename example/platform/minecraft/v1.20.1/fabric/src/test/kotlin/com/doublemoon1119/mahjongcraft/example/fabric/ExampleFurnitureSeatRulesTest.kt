package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleFurnitureSeatRules
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleSeatAttempt
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證範例家具的占用、失敗清理與坐墊穩定識別規則。 */
class ExampleFurnitureSeatRulesTest {
    /** 正常入座會完成定位，不還原乘坐者或刪除使用中的座位。 */
    @Test
    fun `successful seating finishes without restoring or discarding`() {
        var positioned = false
        val result = ExampleSeatAttempt.run(
            create = { Unit },
            spawn = { true },
            mount = { true },
            finish = {
                positioned = true
                true
            },
            restore = { error("Successful seating must not restore the rider") },
            discard = { error("Successful seating must retain its seat") },
        )
        assertTrue(result)
        assertTrue(positioned)
    }

    /** 已占用或正在其他載具上的玩家都不能取得空位結果。 */
    @Test
    fun `occupied target is not available`() {
        assertEquals(SeatState.OCCUPIED, ExampleFurnitureSeatRules.occupancyState(hasVehicle = false, occupied = true))
        assertEquals(SeatState.UNSUPPORTED, ExampleFurnitureSeatRules.occupancyState(hasVehicle = true, occupied = false))
        assertEquals(SeatState.AVAILABLE, ExampleFurnitureSeatRules.occupancyState(hasVehicle = false, occupied = false))
    }

    /** 定位失敗會清理自己建立的支撐，未上座時不還原或移動玩家。 */
    @Test
    fun `failed ride restores only after riding started`() {
        var restored = false
        var discarded = false
        val result = ExampleSeatAttempt.run(
            create = { Unit },
            spawn = { true },
            mount = { true },
            finish = { false },
            restore = { restored = true },
            discard = { discarded = true },
        )
        assertFalse(result)
        assertTrue(restored)
        assertTrue(discarded)

        restored = false
        discarded = false
        val notMounted = ExampleSeatAttempt.run(
            create = { Unit },
            spawn = { true },
            mount = { false },
            finish = { true },
            restore = { restored = true },
            discard = { discarded = true },
        )
        assertFalse(notMounted)
        assertFalse(restored)
        assertTrue(discarded)
    }

    /** 重載前後以維度與 UUID 識別同一坐墊。 */
    @Test
    fun `entity identity uses stable uuid`() {
        val uuid = "b7fdb89f-ef8f-4bfd-bf14-5a5278d7a9ee"
        val first = ExampleFurnitureSeatRules.cushionIdentity("minecraft:overworld", uuid)
        val afterReload = ExampleFurnitureSeatRules.cushionIdentity("minecraft:overworld", uuid)
        assertEquals(first, afterReload)
        assertEquals(uuid, first.key)
        assertEquals("minecraft:entity", first.kind)
    }

    /** 生成失敗不移動乘坐者，也不留下自己建立的支撐資源。 */
    @Test
    fun `failed spawn removes every temporary support without moving rider`() {
        val supports = mutableListOf<Any>()
        var moved = false
        val result = ExampleSeatAttempt.run(
            create = { Any().also(supports::add) },
            spawn = { false },
            mount = {
                moved = true
                true
            },
            finish = { true },
            restore = { error("An unmounted rider must not be restored") },
            discard = { supports.remove(it) },
        )
        assertFalse(result)
        assertFalse(moved)
        assertTrue(supports.isEmpty())
    }

    /** 還原乘坐者時出錯，仍須移除自己建立的暫時支撐。 */
    @Test
    fun `temporary support is discarded even when restore throws`() {
        var discarded = false
        assertFailsWith<IllegalStateException> {
            ExampleSeatAttempt.run(
                create = { Unit },
                spawn = { true },
                mount = { true },
                finish = { false },
                restore = { error("restore failed") },
                discard = { discarded = true },
            )
        }
        assertTrue(discarded)
    }
}
