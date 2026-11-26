package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證項目依登記表分批取出，且全部寫入後的工作依序執行。 */
class RegistrationQueueTest {
    /** 取出某個登記表時只拿到該登記表的項目，依加入順序排列，且取出時不建立物件。 */
    @Test
    fun `takes entries of one registry in order`() {
        val queue = RegistrationQueue<String, String>()
        queue.add(registry = "stat", id = "first") { "first" }
        queue.add(registry = "sound", id = "sound") { error("Sound must wait for its registry") }
        queue.add(registry = "stat", id = "second") { "second" }

        val taken = queue.take("stat")

        assertEquals(listOf("first", "second"), taken.map { it.id })
        assertEquals(listOf("first", "second"), taken.map { it.create() })
        assertEquals(emptyList(), queue.take("stat").map { it.id })
    }

    /** 登記表已經登記過後，再加入的項目會被拒絕。 */
    @Test
    fun `rejects entries added after their registry is registered`() {
        val queue = RegistrationQueue<String, String>()
        queue.take("stat")

        assertFailsWith<IllegalStateException> { queue.add(registry = "stat", id = "late") { "late" } }
    }

    /** 全部寫入後的工作依加入順序執行一次。 */
    @Test
    fun `runs after registration actions once in order`() {
        val queue = RegistrationQueue<String, String>()
        val actions = mutableListOf<Int>()
        queue.afterRegistration { actions += 1 }
        queue.afterRegistration { actions += 2 }

        queue.runAfterRegistration()
        queue.runAfterRegistration()

        assertEquals(listOf(1, 2), actions)
    }
}
