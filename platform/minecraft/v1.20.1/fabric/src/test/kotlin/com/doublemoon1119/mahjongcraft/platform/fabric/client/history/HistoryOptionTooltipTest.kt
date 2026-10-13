package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import net.minecraft.text.Text
import net.minecraft.util.Formatting
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證選項 tooltip 依穩定索引標示選中項，而非比較顯示文字。 */
class HistoryOptionTooltipTest {
    /** 顯示文字相同時仍只標示指定索引。 */
    @Test
    fun `same labels use the selected index`() {
        val text = historyOptionTooltip(
            title = Text.literal("title"),
            current = Text.literal("same"),
            selectedIndex = 1,
            options = listOf(Text.literal("same"), Text.literal("same")),
        )
        val options = text.siblings.filter { it.string == "same" }
        assertEquals(2, options.size)
        assertEquals(Formatting.WHITE.colorValue, options[0].style.color?.rgb)
        assertEquals(Formatting.GREEN.colorValue, options[1].style.color?.rgb)
    }
}
