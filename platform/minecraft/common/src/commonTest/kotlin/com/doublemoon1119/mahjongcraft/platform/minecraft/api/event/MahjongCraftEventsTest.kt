package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** MahjongCraft 公開事件訂閱 API 的註冊與情境測試。 */
class MahjongCraftEventsTest {
    /** 每個測試使用獨立訂閱點，註冊順序會保留。 */
    @Test
    fun `listeners are returned in registration order`() {
        val event = MahjongCraftEvent.create<() -> Unit>()
        val first: () -> Unit = {}
        val second: () -> Unit = {}

        event.register(first)
        event.register(second)

        assertEquals(2, event.listenersSnapshot().size)
        assertSame(first, event.listenersSnapshot()[0])
        assertSame(second, event.listenersSnapshot()[1])
    }

    /** 鎖定後不接受新的監聽者。 */
    @Test
    fun `registration is rejected after lock`() {
        val event = MahjongCraftEvent.create<() -> Unit>()
        event.lockRegistration()

        assertFailsWith<IllegalStateException> { event.register {} }
    }

    /** 監聽者快照不會因後續註冊而改變。 */
    @Test
    fun `listener snapshot is isolated from later registrations`() {
        val event = MahjongCraftEvent.create<() -> Unit>()
        val first: () -> Unit = {}
        val second: () -> Unit = {}
        event.register(first)
        val snapshot = event.listenersSnapshot()
        event.register(second)

        assertEquals(listOf(first), snapshot)
        assertEquals(listOf(first, second), event.listenersSnapshot())
        assertFails {
            (snapshot as MutableList<() -> Unit>).add {}
        }
        assertEquals(listOf(first), snapshot)
    }

    /** 事件情境保存位置與 session，且可判斷是否屬於相同 session。 */
    @Test
    fun `event context keeps location and session identity`() {
        val sessionId = Uuid.random()
        val location = TableLocation.create("minecraft:overworld", 1, 2, 3)
        val context = GameEventContext.create(location, sessionId)

        assertEquals(location, context.tableLocation)
        assertTrue(context.sessionId == sessionId)
        assertTrue(context.sessionId != Uuid.random())
    }
}
