package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證世界面板只在世界繪製期間收集，並在世界畫完時由遠到近畫出。 */
class WorldPanelQueueTest {
    /** 不在世界繪製期間時不收集，呼叫端改為立刻畫。 */
    @Test
    fun `panels outside a world frame are not collected`() {
        val queue = WorldPanelQueue()

        assertFalse(queue.submit(1.0) {})
        assertTrue(queue.drain().isEmpty())
    }

    /** 世界繪製期間只收集、不畫，畫完時由遠到近交出。 */
    @Test
    fun `collected panels are drawn far to near`() {
        val queue = WorldPanelQueue()
        val drawn = mutableListOf<String>()
        queue.beginFrame()

        assertTrue(queue.submit(4.0) { drawn += "middle" })
        assertTrue(queue.submit(1.0) { drawn += "near" })
        assertTrue(queue.submit(9.0) { drawn += "far" })
        assertTrue(drawn.isEmpty())

        queue.drain().forEach { it() }
        assertEquals(listOf("far", "middle", "near"), drawn)
    }

    /** 畫完後清空並停止收集，同一幀的補畫不會再畫一次。 */
    @Test
    fun `drained panels are not drawn again`() {
        val queue = WorldPanelQueue()
        queue.beginFrame()
        queue.submit(1.0) {}

        assertEquals(1, queue.drain().size)
        assertTrue(queue.drain().isEmpty())
        assertFalse(queue.collecting)
        assertFalse(queue.submit(1.0) {})
    }

    /** 新的一幀丟掉上一幀沒畫到的面板。 */
    @Test
    fun `a new frame drops leftovers`() {
        val queue = WorldPanelQueue()
        queue.beginFrame()
        queue.submit(1.0) {}

        queue.beginFrame()

        assertTrue(queue.collecting)
        assertTrue(queue.drain().isEmpty())
    }
}
