package com.doublemoon1119.mahjongcraft.platform.fabric.server.time

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證世界 tick 等待佇列不依賴真實時間或 Minecraft 世界。 */
class FabricWorldTickWaitQueueTest {
    /** 完成與停止即使連續呼叫，也不會重複取得同一筆等待。 */
    @Test
    fun `a completed wait cannot be completed or cancelled again`() {
        val queue = FabricWorldTickWaitQueue<String>()
        val results = mutableListOf<String>()
        queue.register("world", 10, { results += "ready" }, { results += "cancelled" })

        val ready = queue.pollReady("world", 10)
        queue.cancel("world").forEach { it() }
        queue.pollReady("world", 11).forEach { it() }
        ready.forEach { it() }

        assertEquals(listOf("ready"), results)
    }

    /** 相同欄位的兩筆等待仍有獨立身分，移除第二筆不會誤移除第一筆。 */
    @Test
    fun `disposal removes only its own registration`() {
        val queue = FabricWorldTickWaitQueue<String>()
        var readyCount = 0
        val callback = { readyCount += 1 }
        val cancel = {}
        val first = queue.register("world", 10, callback, cancel)
        val second = queue.register("world", 10, callback, cancel)

        second.dispose()
        first.dispose()
        queue.pollReady("world", 10).forEach { it() }

        assertEquals(0, readyCount)
    }

    /** 到期項目依 tick 取出，尚未到期項目保留。 */
    @Test
    fun `polls only waits whose target has arrived`() {
        val queue = FabricWorldTickWaitQueue<String>()
        val completed = mutableListOf<String>()
        queue.register("overworld", 10, { completed += "ready" }, { completed += "cancelled" })
        queue.register("overworld", 20, { completed += "later" }, { completed += "cancelled-later" })

        queue.pollReady("overworld", 10).forEach { it() }

        assertEquals(listOf("ready"), completed)
        queue.pollReady("overworld", 20).forEach { it() }
        assertEquals(listOf("ready", "later"), completed)
    }

    /** 取消不會執行完成回呼，且只影響指定世界。 */
    @Test
    fun `cancels waits for one world`() {
        val queue = FabricWorldTickWaitQueue<String>()
        val completed = mutableListOf<String>()
        queue.register("overworld", 10, { completed += "ready" }, { completed += "cancelled" })
        queue.register("nether", 10, { completed += "nether-ready" }, { completed += "nether-cancelled" })

        queue.cancel("overworld").forEach { it() }
        queue.pollReady("nether", 10).forEach { it() }

        assertEquals(listOf("cancelled", "nether-ready"), completed)
    }
}
