package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftCycleButtonKeys
import net.minecraft.text.Text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** 驗證循環切換按鈕的前後選擇與提示框操作說明。 */
class CycleButtonInputTest {
    /** 往下選到最後一項後回到第一項，往上選到第一項後跳到最後一項。 */
    @Test
    fun `next and previous wrap around both ends`() {
        assertEquals(1, CycleButtonInput.nextIndex(currentIndex = 0, size = 3, step = 1))
        assertEquals(0, CycleButtonInput.nextIndex(currentIndex = 2, size = 3, step = 1))
        assertEquals(2, CycleButtonInput.nextIndex(currentIndex = 0, size = 3, step = -1))
        assertEquals(1, CycleButtonInput.nextIndex(currentIndex = 2, size = 3, step = -1))
        assertEquals("c", CycleButtonInput.next(options = listOf("a", "b", "c"), current = "a", step = -1))
        assertEquals("b", CycleButtonInput.next(options = listOf("a", "b", "c"), current = "a", step = 1))
    }

    /** 目前值不在選項中時，往下從第一項開始、往上從最後一項開始。 */
    @Test
    fun `unknown current value starts from the matching end`() {
        assertEquals(0, CycleButtonInput.nextIndex(currentIndex = -1, size = 4, step = 1))
        assertEquals(3, CycleButtonInput.nextIndex(currentIndex = -1, size = 4, step = -1))
        assertEquals(null, CycleButtonInput.next(options = listOf(null, "a", "b"), current = "b", step = 1))
        assertEquals("b", CycleButtonInput.next(options = listOf(null, "a", "b"), current = null, step = -1))
        assertFailsWith<IllegalArgumentException> { CycleButtonInput.nextIndex(currentIndex = 0, size = 0, step = 1) }
    }

    /** 選項至少三個時，提示框原內容後空一行，再接兩行操作說明。 */
    @Test
    fun `hint follows the tooltip after a blank line`() {
        val tooltip = CycleButtonInput.withHint(tooltip = Text.literal("Options"), optionCount = 3)

        assertEquals(listOf("Options", "", MinecraftCycleButtonKeys.CLICK_NEXT, MinecraftCycleButtonKeys.SHIFT_CLICK_PREVIOUS), tooltip.string.split("\n"))
        val lines = CycleButtonInput.withHintLines(lines = listOf(Text.literal("A"), Text.literal("B")), optionCount = 3)
        assertEquals(listOf("A", "B", "", MinecraftCycleButtonKeys.CLICK_NEXT, MinecraftCycleButtonKeys.SHIFT_CLICK_PREVIOUS), lines.map { it.string })
    }

    /** 只有兩個選項或按鈕停用時，提示框維持原樣。 */
    @Test
    fun `two options or inactive buttons get no hint`() {
        val tooltip = Text.literal("Options")
        val lines = listOf(Text.literal("A"))

        assertSame(tooltip, CycleButtonInput.withHint(tooltip = tooltip, optionCount = 2))
        assertSame(tooltip, CycleButtonInput.withHint(tooltip = tooltip, optionCount = 5, active = false))
        assertSame(lines, CycleButtonInput.withHintLines(lines = lines, optionCount = 2))
        assertSame(lines, CycleButtonInput.withHintLines(lines = lines, optionCount = 5, active = false))
    }
}
