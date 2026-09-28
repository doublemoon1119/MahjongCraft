package com.doublemoon1119.mahjongcraft.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [sortedLangFileText] 與 [isLangFileSorted] 的單元測試。 */
class LangFileSortingTest {
    /** 已排序的內容原樣返回。 */
    @Test
    fun `already sorted content is unchanged`() {
        val text = """
            {
              "a.first": "First",
              "b.second": "Second"
            }

        """.trimIndent()

        assertEquals(text, sortedLangFileText(text))
    }

    /** 未排序的條目依 key 字典序重新排列，value 內容不變。 */
    @Test
    fun `unsorted entries are reordered by key`() {
        val text = """
            {
              "b.second": "Second",
              "a.first": "First",
              "c.third": "Third"
            }

        """.trimIndent()
        val expected = """
            {
              "a.first": "First",
              "b.second": "Second",
              "c.third": "Third"
            }

        """.trimIndent()

        assertEquals(expected, sortedLangFileText(text))
    }

    /** 只有結尾逗號隨排序位置調整，value 本身（含跳脫字元）逐字保留。 */
    @Test
    fun `entry values including escape sequences are preserved verbatim`() {
        val text = """
            {
              "b.second": "Line one\nLine two",
              "a.first": "Quoted \"word\""
            }

        """.trimIndent()
        val expected = """
            {
              "a.first": "Quoted \"word\"",
              "b.second": "Line one\nLine two"
            }

        """.trimIndent()

        assertEquals(expected, sortedLangFileText(text))
    }

    /** 排序具冪等性：對已排序的輸出再次排序得到相同結果。 */
    @Test
    fun `sorting is idempotent`() {
        val text = """
            {
              "b.second": "Second",
              "a.first": "First"
            }

        """.trimIndent()

        assertEquals(sortedLangFileText(text), sortedLangFileText(sortedLangFileText(text)))
    }

    /** CRLF 換行的內容照常排序，輸出一律使用 LF。 */
    @Test
    fun `crlf input is sorted into lf output`() {
        val text = "{\r\n  \"b.second\": \"Second\",\r\n  \"a.first\": \"First\"\r\n}\r\n"

        assertEquals("{\n  \"a.first\": \"First\",\n  \"b.second\": \"Second\"\n}\n", sortedLangFileText(text))
    }

    /** 已排序的 CRLF 內容視為已排序，換行符號不影響判斷。 */
    @Test
    fun `sorted crlf content counts as sorted`() {
        assertTrue(isLangFileSorted("{\r\n  \"a.first\": \"First\",\r\n  \"b.second\": \"Second\"\r\n}\r\n"))
        assertFalse(isLangFileSorted("{\r\n  \"b.second\": \"Second\",\r\n  \"a.first\": \"First\"\r\n}\r\n"))
    }

    /** 不符合固定單行格式（缺少開頭 `{`、結尾 `}` 或結尾換行）的內容一律拒絕。 */
    @Test
    fun `malformed input is rejected`() {
        assertFailsWith<IllegalArgumentException> { sortedLangFileText("\"a\": \"A\"\n") }
        assertFailsWith<IllegalArgumentException> { sortedLangFileText("{\n  \"a\": \"A\"\n}") }
    }
}
