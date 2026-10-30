package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證多選子畫面的選取上下限與交換行為。 */
class MultiChoiceSelectionTest {
    /** 最小數量為一時仍可先清空，再選另一項。 */
    @Test
    fun `can replace the only selected option`() {
        val editor = GameConfigEditorSpec.MultiChoice(listOf("example:a", "example:b"), minimumCount = 1)
        val empty = MultiChoiceSelection.toggle(setOf("example:a"), "example:a", editor)

        assertEquals(emptySet(), empty)
        assertFalse(MultiChoiceSelection.canApply(empty, editor))
        assertTrue(MultiChoiceSelection.canApply(MultiChoiceSelection.toggle(empty, "example:b", editor), editor))
    }

    /** 已達上限時新增選項不會改變集合。 */
    @Test
    fun `does not exceed maximum count`() {
        val editor = GameConfigEditorSpec.MultiChoice(listOf("example:a", "example:b"), maximumCount = 1)

        assertEquals(setOf("example:a"), MultiChoiceSelection.toggle(setOf("example:a"), "example:b", editor))
    }

    /** 未知選項不會加入選取集合，也不能被套用。 */
    @Test
    fun `unknown option cannot be toggled or applied`() {
        val editor = GameConfigEditorSpec.MultiChoice(listOf("example:a", "other:a"))
        assertEquals(emptySet(), MultiChoiceSelection.toggle(emptySet(), "unknown:a", editor))
        assertFalse(MultiChoiceSelection.canApply(setOf("unknown:a"), editor))
    }
}
