package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationValue
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 驗證設定文字使用欄位指定的翻譯鍵，不從選項 ID 猜測文字。 */
class GameConfigPresentationTextTest {
    /** 相同路徑的不同選項可以使用各自的語系鍵。 */
    @Test
    fun `choice text uses the explicitly assigned translation for each namespace`() {
        val translations = mapOf(
            "mahjongcraft:riichi/config/game_length/two_winds" to "mahjongcraft.riichi.room.config.option.two_winds",
            "example:two_winds" to "example.room.option.short_match",
            "example.one:two" to "example.first",
            "example:one.two" to "example.second",
        )
        val editor = GameConfigEditorSpec.SingleChoice(translations.keys.toList(), translations)

        translations.forEach { (optionId, key) ->
            val text = gameConfigPresentationText(GameConfigPresentationValue.ChoiceValue(optionId), editor)
            assertEquals(key, (text.content as TranslatableTextContent).key)
        }
    }

    /** 沒有欄位或沒有選項翻譯時，保留完整 ID，不誤套用內建文字。 */
    @Test
    fun `choice without a translation uses its full literal id`() {
        val value = GameConfigPresentationValue.ChoiceValue("example:two_winds")
        val editor = GameConfigEditorSpec.SingleChoice(listOf(value.optionId))

        listOf(gameConfigPresentationText(value), gameConfigPresentationText(value, editor)).forEach { text ->
            assertEquals(value.optionId, text.string)
            assertFalse(text.content is TranslatableTextContent)
        }
    }

    /** 多選摘要依欄位選項順序顯示，而不是依集合插入順序顯示。 */
    @Test
    fun `multi choice text follows editor order`() {
        val editor = GameConfigEditorSpec.MultiChoice(
            optionIds = listOf("example:one", "example:two", "example:three"),
            optionTranslationKeys = mapOf(
                "example:one" to "example.option.one",
                "example:two" to "example.option.two",
                "example:three" to "example.option.three",
            ),
        )

        val text = gameConfigPresentationText(
            value = GameConfigPresentationValue.MultiChoiceValue(setOf("example:three", "example:one")),
            editor = editor,
        )

        assertEquals("example.option.one, example.option.three", text.string)
    }

    /** 歷史設定中的未知選項不能在摘要中被靜默丟棄。 */
    @Test
    fun `multi choice text preserves unknown ids after registered options`() {
        val editor = GameConfigEditorSpec.MultiChoice(listOf("example:known"))
        val first = gameConfigPresentationText(
            value = GameConfigPresentationValue.MultiChoiceValue(linkedSetOf("other:unknown", "example:known")),
            editor = editor,
        )
        val reordered = gameConfigPresentationText(
            value = GameConfigPresentationValue.MultiChoiceValue(linkedSetOf("example:known", "other:unknown")),
            editor = editor,
        )
        assertEquals("example:known, other:unknown", first.string)
        assertEquals(first.string, reordered.string)
    }
}
