package com.doublemoon1119.mahjongcraft.buildlogic.detekt

import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證 MahjongCraft KDoc 規則對不同宣告與換行格式的判定。 */
class KDocRulesTest {
    /** 驗證多行 KDoc 的開頭規則只回報開頭格式問題。 */
    @Test
    fun `opening rule reports malformed multiline opening`() {
        val findings = KDocOpeningLineRule().lint(
            content = "/** description\n * @param value value\n */\nclass Sample(val value: String)",
        )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.isNotBlank())
    }

    /** 驗證描述與不同區塊標籤之間都必須保留空白註解行。 */
    @Test
    fun `tag spacing rule reports each supported declaration tag`() {
        val source = """
            /** Description.
             * @param value value
             */
            fun parameter(value: String) = value

            /** Description.
             * @property value value
             */
            class Property(val value: String)

            /** Description.
             * @return value
             */
            fun returned() = 1

            /** Description.
             * @throws IllegalStateException when invalid
             */
            fun throwing() = Unit
        """.trimIndent()

        val findings = KDocTagSpacingRule().lint(source)

        assertEquals(4, findings.size)
        assertTrue(findings.all { it.message.isNotBlank() })
    }

    /** 驗證合法的單行、空描述與描述段落格式不會被回報。 */
    @Test
    fun `rules accept valid KDoc layouts`() {
        val source = """
            /** Single-line documentation. */
            class SingleLine

            /**
             * Description.
             *
             * @param value value
             */
            class Valid(val value: String)

            /**
             * @param value value
             */
            fun noDescription(value: String) = value

            /**
             * Inline `@word` and fenced example:
             * ```text
             * @not-a-tag
             * ```
             */
            fun example() = Unit
        """.trimIndent()

        assertTrue(KDocOpeningLineRule().lint(source).isEmpty())
        assertTrue(KDocTagSpacingRule().lint(source).isEmpty())
    }

    /** 驗證 CRLF 與巢狀宣告仍使用相同的規則。 */
    @Test
    fun `rules handle CRLF and nested declarations`() {
        val source = "/**\r\n * Description.\r\n *\r\n * @property value value.\r\n */\r\nclass Outer {\r\n    /**\r\n     * Description.\r\n     * @return value.\r\n     */\r\n    fun inner() = 1\r\n}"

        assertEquals(0, KDocOpeningLineRule().lint(source).size)
        val findings = KDocTagSpacingRule().lint(source)
        assertEquals(1, findings.size)
    }
}
