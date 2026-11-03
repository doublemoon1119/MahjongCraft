package com.doublemoon1119.mahjongcraft.example.fabric.character

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 人偶接管只恢復自己的狀態，沒有刪除實體或生成新參與者的能力。 */
class ExampleActorControlTest {
    /** 重複或過期的釋放不影響下一個 handle，原始狀態只恢復一次。 */
    @Test
    fun `release restores once and old handles cannot release new control`() {
        var state = "standing"
        var restores = 0
        val control = ExampleActorControl(capture = { state }, restore = { saved ->
            state = saved
            restores++
        })
        val first = Any()
        val second = Any()
        assertTrue(control.acquire(first))
        assertFalse(control.acquire(second))
        state = "seated"
        control.release(first)
        control.release(first)
        assertEquals("standing", state)
        assertEquals(1, restores)
        assertNull(control.savedState)
        assertTrue(control.acquire(second))
        state = "seated again"
        control.release(first)
        assertTrue(control.isActive)
        assertEquals("seated again", state)
        control.release(second)
        assertEquals("standing", state)
        assertEquals(2, restores)
    }

    /** 準備過程即使只改到一半就失敗，也能沿相同 token 恢復。 */
    @Test
    fun `partially prepared control restores its original state`() {
        var state = 5
        val control = ExampleActorControl(capture = { state }, restore = { state = it })
        val token = Any()
        assertFailsWith<IllegalStateException> {
            try {
                assertTrue(control.acquire(token))
                state = 9
                error("Preparation failed")
            } finally {
                control.release(token)
            }
        }
        assertEquals(5, state)
        assertFalse(control.isActive)
    }
}
