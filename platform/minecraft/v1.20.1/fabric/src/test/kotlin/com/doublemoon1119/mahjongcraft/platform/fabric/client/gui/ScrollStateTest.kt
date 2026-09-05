package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證捲動 index 在滾輪、拖曳與版面重建時都收斂在合法範圍內。 */
class ScrollStateTest {
    /** 滾輪往下捲動到底部後，繼續往下捲不得超出上限（重現曾經一瞬間超出又復原的問題）。 */
    @Test
    fun `scrolling past the bottom stays clamped at the maximum`() {
        val state = ScrollState()

        repeat(10) { state.scrollBy(-1.0, 3) }

        assertEquals(3, state.index)
    }

    /** 滾輪往上捲動到頂部後，繼續往上捲不得低於零。 */
    @Test
    fun `scrolling past the top stays clamped at zero`() {
        val state = ScrollState()

        repeat(10) { state.scrollBy(1.0, 3) }

        assertEquals(0, state.index)
    }

    /** 已達上限時滾輪不應回報變更。 */
    @Test
    fun `scrollBy reports no change once at the maximum`() {
        val state = ScrollState()
        repeat(3) { state.scrollBy(-1.0, 3) }

        assertFalse(state.scrollBy(-1.0, 3))
    }

    /** 項目總數在重建時減少，目前 index 必須收斂回新的合法範圍。 */
    @Test
    fun `clamp pulls index back within a shrunk maximum`() {
        val state = ScrollState()
        repeat(5) { state.scrollBy(-1.0, 5) }
        assertEquals(5, state.index)

        state.clamp(2)

        assertEquals(2, state.index)
    }

    /** 開始拖曳時記錄抓取偏移，之後以相同抓取點換算才不會跳到以游標為 thumb 上界的位置。 */
    @Test
    fun `dragging preserves the grabbed point inside the thumb`() {
        val state = ScrollState()
        val layout = ScrollbarLayout(20, 120, 10, 4, 3, 12)

        state.beginDrag(layout.thumbTop + 10.0, layout)
        assertTrue(state.dragging)
        assertEquals(3, state.index)

        state.dragTo(80.0, layout.copy(scrollIndex = state.index))
        assertEquals(5, state.index)
    }

    /** 已在合法範圍邊界時開始拖曳不應回報變更，避免呼叫端在邊界重複觸發不必要的 widget 重建。 */
    @Test
    fun `beginDrag reports no change when already at the boundary`() {
        val state = ScrollState()
        val layout = ScrollbarLayout(20, 120, 10, 4, 6, 12)
        state.beginDrag(layout.thumbTop + layout.thumbHeight.toDouble(), layout)
        assertEquals(6, state.index)

        val changed = state.beginDrag(500.0, layout.copy(scrollIndex = state.index))

        assertFalse(changed)
    }

    /** 結束拖曳後 dragging 必須回到 false。 */
    @Test
    fun `endDrag clears the dragging flag`() {
        val state = ScrollState()
        val layout = ScrollbarLayout(20, 120, 10, 4, 0, 12)
        state.beginDrag(layout.thumbTop.toDouble(), layout)

        state.endDrag()

        assertFalse(state.dragging)
    }

    /** reset 直接回到頂端，供切換分類等情境使用。 */
    @Test
    fun `reset returns to the top`() {
        val state = ScrollState()
        repeat(5) { state.scrollBy(-1.0, 5) }

        state.reset()

        assertEquals(0, state.index)
    }
}
