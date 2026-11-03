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
            ExampleActorReturnToSeat.isNear(
                1.5,
                64.0,
                0.5,
                0.5,
                64.0,
                0.5,
            ),
        )
        assertTrue(
            ExampleActorReturnToSeat.isNear(
                6.5,
                64.0,
                0.5,
                0.5,
                64.0,
                0.5,
            ),
        )
        assertFalse(
            ExampleActorReturnToSeat.isNear(
                6.6,
                64.0,
                0.5,
                0.5,
                64.0,
                0.5,
            ),
        )
        assertFalse(
            ExampleActorReturnToSeat.isNear(
                Double.NaN,
                64.0,
                0.5,
                0.5,
                64.0,
                0.5,
            ),
        )
        assertFalse(
            ExampleActorReturnToSeat.isNear(
                0.5,
                Double.POSITIVE_INFINITY,
                0.5,
                0.5,
                64.0,
                0.5,
            ),
        )
    }

    /** 每次回呼的新物件或整圈角度不應反覆讓人偶下座，真正換位才需要處理。 */
    @Test
    fun `only changed seat values trigger the example relocation behavior`() {
        val previous = SeatGeometry(
            0.5,
            64.0,
            2.5,
            0f,
        )
        assertTrue(
            ExampleActorReturnToSeat.sameSeat(
                previous,
                SeatGeometry(
                    0.5,
                    64.0,
                    2.5,
                    360f,
                ),
            ),
        )
        assertFalse(
            ExampleActorReturnToSeat.sameSeat(
                previous,
                SeatGeometry(
                    2.5,
                    64.0,
                    0.5,
                    90f,
                ),
            ),
        )
        assertFalse(
            ExampleActorReturnToSeat.sameSeat(
                previous,
                SeatGeometry(
                    0.5,
                    64.0,
                    2.5,
                    180f,
                ),
            ),
        )
    }

    /** 已保存的正確乘坐高度不必離座，保存於舊位置的人偶則交由範例重新安排。 */
    @Test
    fun `restored seated positions use public seat tolerances without requiring the exact feet height`() {
        val seat = SeatGeometry(
            0.5,
            64.0,
            2.5,
            0f,
        )
        assertTrue(
            ExampleActorReturnToSeat.isAtSeat(
                0.5,
                63.9765,
                2.5,
                seat,
            ),
        )
        assertFalse(
            ExampleActorReturnToSeat.isAtSeat(
                2.5,
                64.0,
                0.5,
                seat,
            ),
        )
        assertFalse(
            ExampleActorReturnToSeat.isAtSeat(
                0.75,
                64.0,
                2.75,
                seat,
            ),
        )
        assertFalse(
            ExampleActorReturnToSeat.isAtSeat(
                0.5,
                65.1,
                2.5,
                seat,
            ),
        )
    }

    /** 未載入、受阻或被乘客占用時，先等待，不查地面或執行移動。 */
    @Test
    fun `blocked standing space leaves the actor in place without probing support`() {
        var supportChecks = 0
        var movements = 0
        assertFalse(
            ExampleActorReturnToSeat.standIfSafe(
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
            ExampleActorReturnToSeat.standIfSafe(
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
            ExampleActorReturnToSeat.standIfSafe(
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
