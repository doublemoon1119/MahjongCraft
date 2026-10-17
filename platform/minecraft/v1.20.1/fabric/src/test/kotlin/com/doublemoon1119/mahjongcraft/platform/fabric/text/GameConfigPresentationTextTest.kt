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
            "mahjongcraft:two_winds" to "mahjongcraft.room.config.option.two_winds",
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
}
