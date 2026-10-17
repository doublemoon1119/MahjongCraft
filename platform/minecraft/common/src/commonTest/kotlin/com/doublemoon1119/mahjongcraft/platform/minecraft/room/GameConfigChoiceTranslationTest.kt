package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 驗證單選欄位由設定定義提供翻譯鍵，未指定時保留完整選項 ID。 */
class GameConfigChoiceTranslationTest {
    /** 第三方選項能取得明確指定的翻譯鍵。 */
    @Test
    fun `single choice resolves explicitly assigned translation key`() {
        val editor = GameConfigEditorSpec.SingleChoice(
            optionIds = listOf("example:two_winds"),
            optionTranslationKeys = mapOf("example:two_winds" to "example.room.option.two_winds"),
        )

        assertEquals("example.room.option.two_winds", editor.translationKeyFor("example:two_winds"))
    }

    /** 不同命名空間可以使用相同路徑而保留各自的翻譯鍵。 */
    @Test
    fun `translation keys distinguish options with the same path`() {
        val editor = GameConfigEditorSpec.SingleChoice(
            optionIds = listOf("first:shared", "second:shared"),
            optionTranslationKeys = mapOf(
                "first:shared" to "first.room.option.shared",
                "second:shared" to "second.room.option.shared",
            ),
        )

        assertEquals("first.room.option.shared", editor.translationKeyFor("first:shared"))
        assertEquals("second.room.option.shared", editor.translationKeyFor("second:shared"))
    }

    /** 未指定翻譯鍵時不會猜測內建鍵，讓呈現層回退到完整 ID。 */
    @Test
    fun `single choice leaves unassigned option without translation key`() {
        val editor = GameConfigEditorSpec.SingleChoice(listOf("example:two_winds"))

        assertNull(editor.translationKeyFor("example:two_winds"))
    }

    /** 翻譯鍵不得指向不存在的選項或使用空字串。 */
    @Test
    fun `single choice rejects invalid translation key mapping`() {
        assertFailsWith<IllegalArgumentException> {
            GameConfigEditorSpec.SingleChoice(
                optionIds = listOf("example:known"),
                optionTranslationKeys = mapOf("example:unknown" to "example.option.unknown"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GameConfigEditorSpec.SingleChoice(
                optionIds = listOf("example:known"),
                optionTranslationKeys = mapOf("example:known" to ""),
            )
        }
    }
}
