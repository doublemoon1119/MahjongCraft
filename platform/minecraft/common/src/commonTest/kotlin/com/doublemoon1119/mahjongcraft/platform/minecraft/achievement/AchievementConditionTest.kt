package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 [AchievementCondition] 的成果、規則與統計門檻比對，以及 [AchievementStatisticIds] 的統計對應。 */
class AchievementConditionTest {
    /** 單一成果在同一次觸發中成立時條件成立。 */
    @Test
    fun `single achievement matches when it is present`() {
        val condition = AchievementCondition(setOf(BuiltInAchievementIds.SELF_DRAW_WIN))

        assertTrue(condition.matches(achievements(BuiltInAchievementIds.WIN, BuiltInAchievementIds.SELF_DRAW_WIN)) { 0 })
        assertFalse(condition.matches(achievements(BuiltInAchievementIds.WIN, BuiltInAchievementIds.DISCARD_WIN)) { 0 })
    }

    /** 多個成果必須在同一次觸發中全部成立；例如兩個役滿同時成立的組合進度。 */
    @Test
    fun `combined achievements require every achievement in the same trigger`() {
        val combined = AchievementCondition(setOf("mahjongcraft:riichi/yakuman/chinroutou", "mahjongcraft:riichi/yakuman/suuankou_tanki"))

        assertTrue(combined.matches(achievements("mahjongcraft:riichi/yakuman/chinroutou", "mahjongcraft:riichi/yakuman/suuankou_tanki")) { 0 })
        assertFalse(combined.matches(achievements("mahjongcraft:riichi/yakuman/chinroutou")) { 0 })
    }

    /** 限定規則時，其他規則的同一個成果不成立。 */
    @Test
    fun `rule restriction only matches the named rule`() {
        val condition = AchievementCondition(setOf(BuiltInAchievementIds.WIN), ruleModuleId = BuiltInRuleModuleIds.RIICHI)

        assertTrue(condition.matches(achievements(BuiltInAchievementIds.WIN, rule = BuiltInRuleModuleIds.RIICHI)) { 0 })
        assertFalse(condition.matches(achievements(BuiltInAchievementIds.WIN, rule = BuiltInRuleModuleIds.TAIWAN)) { 0 })
    }

    /** 統計門檻剛好達到時成立，差一點時不成立。 */
    @Test
    fun `statistic threshold matches at the threshold and not below`() {
        val condition = AchievementCondition(
            achievementIds = setOf(BuiltInAchievementIds.WIN),
            statistic = StatisticThreshold(AchievementStatisticIds.WINS, atLeast = 100),
        )
        val win = achievements(BuiltInAchievementIds.WIN)

        assertTrue(condition.matches(win) { statisticId -> if (statisticId == AchievementStatisticIds.WINS) 100 else 0 })
        assertFalse(condition.matches(win) { statisticId -> if (statisticId == AchievementStatisticIds.WINS) 99 else 0 })
    }

    /** 每個通用成果累加對應的統計；沒有統計的成果不累加。 */
    @Test
    fun `generic achievements map to their statistics`() {
        assertEquals(
            setOf(AchievementStatisticIds.WINS, AchievementStatisticIds.SELF_DRAW_WINS),
            AchievementStatisticIds.statisticsFor(setOf(BuiltInAchievementIds.WIN, BuiltInAchievementIds.SELF_DRAW_WIN)),
        )
        assertEquals(setOf(AchievementStatisticIds.DEAL_INS), AchievementStatisticIds.statisticsFor(setOf(BuiltInAchievementIds.DEAL_IN, BuiltInAchievementIds.DOUBLE_DEAL_IN)))
        assertEquals(emptySet(), AchievementStatisticIds.statisticsFor(setOf(BuiltInAchievementIds.LAST_PLACE, "mahjongcraft:riichi/ippatsu")))
    }

    private fun achievements(vararg ids: String, rule: String = BuiltInRuleModuleIds.RIICHI) = PlayerAchievements(
        playerId = Uuid.random(),
        matchId = Uuid.random(),
        ruleModuleId = rule,
        achievementIds = ids.toSet(),
    )
}
