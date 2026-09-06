package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證操作卡固定排序為吃、碰、槓、榮和、自摸，且槓的三種型態彼此排序相同。 */
class DecisionHudActionDisplayOrderTest {
    /** 由左至右排序後，內建動作應精確符合吃、碰、槓、榮和、自摸的順序。 */
    @Test
    fun `sorting shuffled built-in actions restores chi pon kan ron tsumo order`() {
        val shuffled = listOf(
            "mahjongcraft:tsumo",
            "mahjongcraft:kan_open",
            "mahjongcraft:ron",
            "mahjongcraft:chi",
            "mahjongcraft:pon",
        )

        val sorted = shuffled.sortedBy { it.actionDisplayPriority() }

        assertEquals(
            listOf("mahjongcraft:chi", "mahjongcraft:pon", "mahjongcraft:kan_open", "mahjongcraft:ron", "mahjongcraft:tsumo"),
            sorted,
        )
    }

    /** 三種槓型態必須排在同一個優先順序，才能與碰、榮和維持穩定的相對順序。 */
    @Test
    fun `every kan variant shares the same priority`() {
        val priorities = listOf("mahjongcraft:kan_open", "mahjongcraft:kan_closed", "mahjongcraft:kan_added")
            .map { it.actionDisplayPriority() }

        assertTrue(priorities.distinct().size == 1, "kan variants must share one priority: $priorities")
    }

    /** 未列在固定順序中的 ID（例如第三方規則模組的特殊動作）必須排在已知動作之後。 */
    @Test
    fun `unknown action ids sort after every known action`() {
        val knownPriorities = listOf(
            "mahjongcraft:chi",
            "mahjongcraft:pon",
            "mahjongcraft:kan_open",
            "mahjongcraft:ron",
            "mahjongcraft:tsumo",
        ).map { it.actionDisplayPriority() }

        val unknownPriority = "riichi:kyuushu_kyuuhai".actionDisplayPriority()

        assertTrue(knownPriorities.all { it < unknownPriority }, "unknown action must sort after all known actions")
    }
}
