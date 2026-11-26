package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    /** 多選值只接受已登記的選項，並遵守選取數量上下限。 */
    @Test
    fun `multi choice validates selected options and count`() {
        val editor = GameConfigEditorSpec.MultiChoice(
            optionIds = listOf("example:one", "example:two", "example:three"),
            minimumCount = 1,
            maximumCount = 2,
        )

        assertTrue(editor.accepts(GameConfigPresentationValue.MultiChoiceValue(setOf("example:one"))))
        assertTrue(editor.accepts(GameConfigPresentationValue.MultiChoiceValue(setOf("example:one", "example:two"))))
        assertFalse(editor.accepts(GameConfigPresentationValue.MultiChoiceValue(emptySet())))
        assertFalse(editor.accepts(GameConfigPresentationValue.MultiChoiceValue(setOf("example:one", "example:two", "example:three"))))
        assertFalse(editor.accepts(GameConfigPresentationValue.MultiChoiceValue(setOf("other:one"))))
        assertFalse(editor.accepts(GameConfigPresentationValue.ChoiceValue("example:one")))
    }

    /** 多選編輯器會拒絕不合法的選項清單與數量限制。 */
    @Test
    fun `multi choice rejects invalid definition`() {
        assertFailsWith<IllegalArgumentException> {
            GameConfigEditorSpec.MultiChoice(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            GameConfigEditorSpec.MultiChoice(listOf("example:one", "example:one"))
        }
        assertFailsWith<IllegalArgumentException> {
            GameConfigEditorSpec.MultiChoice(listOf("example:one"), minimumCount = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            GameConfigEditorSpec.MultiChoice(listOf("example:one"), maximumCount = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            GameConfigEditorSpec.MultiChoice(
                optionIds = listOf("example:one"),
                optionTranslationKeys = mapOf("example:unknown" to "example.option.unknown"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GameConfigEditorSpec.MultiChoice(
                optionIds = listOf("example:one"),
                optionDescriptionTranslationKeys = mapOf("example:unknown" to "example.option.unknown.description"),
            )
        }
    }

    /** 多選選項可取得明確指定的翻譯鍵。 */
    @Test
    fun `multi choice resolves explicitly assigned translation key`() {
        val editor = GameConfigEditorSpec.MultiChoice(
            optionIds = listOf("example:one"),
            optionTranslationKeys = mapOf("example:one" to "example.option.one"),
            optionDescriptionTranslationKeys = mapOf("example:one" to "example.option.one.description"),
        )

        assertEquals("example.option.one", editor.translationKeyFor("example:one"))
        assertEquals("example.option.one.description", editor.descriptionTranslationKeyFor("example:one"))
        assertNull(editor.translationKeyFor("example:unknown"))
        assertNull(editor.descriptionTranslationKeyFor("example:unknown"))
    }

    /** 零項選取可被明確允許，且數量限制與選項 ID 都在建構時驗證。 */
    @Test
    fun `multi choice supports an explicitly empty selection and rejects malformed bounds`() {
        val editor = GameConfigEditorSpec.MultiChoice(listOf("example:one"), maximumCount = 0)
        assertTrue(editor.accepts(GameConfigPresentationValue.MultiChoiceValue(emptySet())))
        assertFalse(editor.accepts(GameConfigPresentationValue.MultiChoiceValue(setOf("example:one"))))
        assertFailsWith<IllegalArgumentException> {
            GameConfigEditorSpec.MultiChoice(listOf("example:one"), minimumCount = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            GameConfigEditorSpec.MultiChoice(listOf("missing_namespace"))
        }
        assertFailsWith<IllegalArgumentException> {
            GameConfigEditorSpec.MultiChoice(listOf("example:one"), optionTranslationKeys = mapOf("example:one" to " "))
        }
    }
}
