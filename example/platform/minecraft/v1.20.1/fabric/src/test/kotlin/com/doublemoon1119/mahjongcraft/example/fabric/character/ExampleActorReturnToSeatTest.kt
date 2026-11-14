package com.doublemoon1119.mahjongcraft.example.fabric.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import net.minecraft.util.math.Box
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 回座是範例自己的有界行為，不會拉回跑遠或位置無效的人偶。 */
class ExampleActorReturnToSeatTest {
    /** 拆椅後安全移到鄰格，仍屬於範例願意回座的距離。 */
    @Test
    fun `a nearby safe dismount can return but a distant actor cannot`() {
        assertTrue(
            actual = ExampleActorReturnToSeat.isNear(
                x = 1.5,
                y = 64.0,
                z = 0.5,
                seatX = 0.5,
                seatY = 64.0,
                seatZ = 0.5,
            ),
        )
        assertTrue(
            actual = ExampleActorReturnToSeat.isNear(
                x = 6.5,
                y = 64.0,
                z = 0.5,
                seatX = 0.5,
                seatY = 64.0,
                seatZ = 0.5,
            ),
        )
        assertFalse(
            actual = ExampleActorReturnToSeat.isNear(
                x = 6.6,
                y = 64.0,
                z = 0.5,
                seatX = 0.5,
                seatY = 64.0,
                seatZ = 0.5,
            ),
        )
        assertFalse(
            actual = ExampleActorReturnToSeat.isNear(
                x = Double.NaN,
                y = 64.0,
                z = 0.5,
                seatX = 0.5,
                seatY = 64.0,
                seatZ = 0.5,
            ),
        )
        assertFalse(
            actual = ExampleActorReturnToSeat.isNear(
                x = 0.5,
                y = Double.POSITIVE_INFINITY,
                z = 0.5,
                seatX = 0.5,
                seatY = 64.0,
                seatZ = 0.5,
            ),
        )
    }

    /** 每次回呼的新物件或整圈角度不應反覆讓人偶下座，真正換位才需要處理。 */
    @Test
    fun `only changed seat values trigger the example relocation behavior`() {
        val previous = SeatGeometry(
            x = 0.5,
            y = 64.0,
            z = 2.5,
            yaw = 0f,
        )
        assertTrue(
            actual = ExampleActorReturnToSeat.sameSeat(
                previous = previous,
                current = SeatGeometry(
                    x = 0.5,
                    y = 64.0,
                    z = 2.5,
                    yaw = 360f,
                ),
            ),
        )
        assertFalse(
            actual = ExampleActorReturnToSeat.sameSeat(
                previous = previous,
                current = SeatGeometry(
                    x = 2.5,
                    y = 64.0,
                    z = 0.5,
                    yaw = 90f,
                ),
            ),
        )
        assertFalse(
            actual = ExampleActorReturnToSeat.sameSeat(
                previous = previous,
                current = SeatGeometry(
                    x = 0.5,
                    y = 64.0,
                    z = 2.5,
                    yaw = 180f,
                ),
            ),
        )
    }

    /** 已保存的正確乘坐高度不必離座，保存於舊位置的人偶則交由範例重新安排。 */
    @Test
    fun `restored seated positions use public seat tolerances without requiring the exact feet height`() {
        val seat = SeatGeometry(
            x = 0.5,
            y = 64.0,
            z = 2.5,
            yaw = 0f,
        )
        assertTrue(
            actual = ExampleActorReturnToSeat.isAtSeat(
                x = 0.5,
                y = 63.9765,
                z = 2.5,
                seat = seat,
            ),
        )
        assertFalse(
            actual = ExampleActorReturnToSeat.isAtSeat(
                x = 2.5,
                y = 64.0,
                z = 0.5,
                seat = seat,
            ),
        )
        assertFalse(
            actual = ExampleActorReturnToSeat.isAtSeat(
                x = 0.75,
                y = 64.0,
                z = 2.75,
                seat = seat,
            ),
        )
        assertFalse(
            actual = ExampleActorReturnToSeat.isAtSeat(
                x = 0.5,
                y = 65.1,
                z = 2.5,
                seat = seat,
            ),
        )
    }

    /** 未載入、受阻或被乘客占用時，先等待，不查地面或執行移動。 */
    @Test
    fun `blocked standing space leaves the actor in place without probing support`() {
        var supportChecks = 0
        var movements = 0
        assertFalse(
            actual = ExampleActorReturnToSeat.standIfSafe(
                body = standingBody(),
                isClear = { false },
                hasSupport = {
                    supportChecks++
                    true
                },
                move = { movements++ },
            ),
        )
        assertEquals(0, supportChecks)
        assertEquals(0, movements)
    }

    /** 沒有腳下支撐時，不把人偶傳送到懸空位置。 */
    @Test
    fun `empty standing space without ground support does not move the actor`() {
        var movements = 0
        assertFalse(
            actual = ExampleActorReturnToSeat.standIfSafe(
                body = standingBody(),
                isClear = { true },
                hasSupport = { false },
                move = { movements++ },
            ),
        )
        assertEquals(0, movements)
    }

    /** 空間與支撐檢查使用同一個站立範圍，全部通過後才移動一次。 */
    @Test
    fun `clear supported standing space moves the actor only after both checks`() {
        val body = standingBody()
        val events = mutableListOf<String>()
        assertTrue(
            actual = ExampleActorReturnToSeat.standIfSafe(
                body = body,
                isClear = { checked ->
                    assertSame(body, checked)
                    events += "space"
                    true
                },
                hasSupport = { checked ->
                    assertSame(body, checked)
                    events += "support"
                    true
                },
                move = { events += "move" },
            ),
        )
        assertEquals(listOf("space", "support", "move"), events)
    }

    /** 人偶在指定位置的完整身體範圍。 */
    private fun standingBody(): Box = Box(
        0.25,
        64.0,
        0.25,
        0.75,
        65.975,
        0.75,
    )
}
