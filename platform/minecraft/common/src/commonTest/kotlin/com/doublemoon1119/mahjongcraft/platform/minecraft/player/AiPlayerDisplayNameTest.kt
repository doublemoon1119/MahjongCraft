package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [aiPlayerDisplayName] 的穩定 AI 順序測試。 */
class AiPlayerDisplayNameTest {
    @Test
    fun `test AI names use AI-only order`() {
        val first = Uuid.random()
        val second = Uuid.random()

        assertEquals("AI 1", aiPlayerDisplayName(first, listOf(first, second)))
        assertEquals("AI 2", aiPlayerDisplayName(second, listOf(first, second)))
    }

    @Test
    fun `test missing AI order uses stable UUID fallback`() {
        val playerId = Uuid.parse("12345678-1234-1234-1234-123456789abc")

        assertEquals("AI-123456", aiPlayerDisplayName(playerId, emptyList()))
    }
}
