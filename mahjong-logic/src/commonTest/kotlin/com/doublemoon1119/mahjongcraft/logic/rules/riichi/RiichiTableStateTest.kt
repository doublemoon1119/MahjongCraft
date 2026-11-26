package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證桌況供託的局部更新不會修改來源或本局進度。 */
class RiichiTableStateTest {
    /** 增加一千點供託時只產生新版本，原桌況與牌山進度保持不變。 */
    @Test
    fun `pot update preserves wall progress and the original table`() {
        val progress = RiichiWallProgress(completedSupplementalDrawCount = 1, revealedKanDoraCount = 1)
        val table = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            components = riichiTableComponents(potPoints = 5000, wallProgress = progress),
        )
        val updated = table.copy(
            components = table.components.with(RiichiTableComponents.POT_POINTS, table.riichiPotPoints() + RIICHI_STICK_POINTS),
        )
        assertEquals(6000, updated.riichiPotPoints())
        assertEquals(5000, table.riichiPotPoints())
        assertEquals(progress, updated.riichiWallProgress())
        assertEquals(progress, table.riichiWallProgress())
    }
}
