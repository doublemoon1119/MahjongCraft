package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Validates the typed JSON representation shared by entity tracking and NBT. */
class RoundInfoLineCodecTest {
    @Test
    fun `round trip preserves number wind and keys`() {
        val lines = listOf(
            RoundInfoLine(
                "example:title",
                listOf(RoundInfoArgument.WindValue(Wind.SOUTH), RoundInfoArgument.Number(2)),
            ),
            RoundInfoLine("example:count", listOf(RoundInfoArgument.Number(7))),
        )

        assertEquals(lines, RoundInfoLineCodec.decode(RoundInfoLineCodec.encode(lines)))
    }

    @Test
    fun `malformed JSON returns empty list`() {
        assertTrue(RoundInfoLineCodec.decode("not-json").isEmpty())
    }

    @Test
    fun `unknown argument kind skips only malformed line`() {
        val encoded = """
            [{"key":"example:bad","args":[{"kind":"future","value":"x"}]},
             {"key":"example:good","args":[{"kind":"number","value":"4"}]}]
        """.trimIndent()

        assertEquals(
            listOf(RoundInfoLine("example:good", listOf(RoundInfoArgument.Number(4)))),
            RoundInfoLineCodec.decode(encoded),
        )
    }

    @Test
    fun `unknown wind value skips malformed line`() {
        val encoded = "[{\"key\":\"example:bad\",\"args\":[{\"kind\":\"wind\",\"value\":\"CENTER\"}]}]"

        assertTrue(RoundInfoLineCodec.decode(encoded).isEmpty())
    }
}
