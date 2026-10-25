package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證成果授予時統計累加與進度條件觸發的順序及去重行為。 */
class AchievementGrantExecutorTest {
    /** 建立一組包含全部通用成果的測試資料。 */
    private fun allAchievements(): PlayerAchievements = PlayerAchievements(
        playerId = Uuid.random(),
        matchId = Uuid.random(),
        ruleModuleId = "mahjongcraft:test_rule",
        achievementIds = setOf(
            BuiltInAchievementIds.MATCH_COMPLETED,
            BuiltInAchievementIds.FIRST_PLACE,
            BuiltInAchievementIds.WIN,
            BuiltInAchievementIds.SELF_DRAW_WIN,
            BuiltInAchievementIds.DISCARD_WIN,
            BuiltInAchievementIds.DEAL_IN,
            BuiltInAchievementIds.EXHAUSTIVE_DRAW,
        ),
    )

    /** 所有通用成果各自只累加對應的一項統計，並依固定順序輸出。 */
    @Test
    fun `all built in achievements increment their statistics once`() {
        val increments = mutableListOf<String>()

        AchievementGrantExecutor.grant(allAchievements(), increments::add) {}

        assertEquals(AchievementStatisticIds.ALL, increments)
    }

    /** 同一成果重複出現時，統計仍只累加一次。 */
    @Test
    fun `duplicate mapped achievements increment a statistic once`() {
        val increments = mutableListOf<String>()
        val achievements = allAchievements().copy(
            achievementIds = setOf(
                BuiltInAchievementIds.WIN,
                BuiltInAchievementIds.SELF_DRAW_WIN,
                BuiltInAchievementIds.WIN,
            ),
        )

        AchievementGrantExecutor.grant(achievements, increments::add) {}

        assertEquals(
            listOf(AchievementStatisticIds.WINS, AchievementStatisticIds.SELF_DRAW_WINS),
            increments,
        )
    }

    /** 統計累加完成後才觸發依統計門檻判定的條件。 */
    @Test
    fun `criterion observes statistic after the increment`() {
        val values = mutableMapOf(AchievementStatisticIds.WINS to 99)
        val achievements = allAchievements().copy(achievementIds = setOf(BuiltInAchievementIds.WIN))
        val condition = AchievementCondition(
            achievementIds = setOf(BuiltInAchievementIds.WIN),
            statistic = StatisticThreshold(AchievementStatisticIds.WINS, 100),
        )
        var triggered = false

        AchievementGrantExecutor.grant(
            achievements = achievements,
            incrementStatistic = { statisticId -> values[statisticId] = values.getValue(statisticId) + 1 },
            triggerCriterion = {
                triggered = condition.matches(achievements, values::getValue)
            },
        )

        assertTrue(triggered)
        assertEquals(100, values.getValue(AchievementStatisticIds.WINS))
    }

    /** 任一統計累加失敗時不觸發進度條件。 */
    @Test
    fun `increment failure does not trigger criterion`() {
        var triggered = false

        try {
            AchievementGrantExecutor.grant(
                achievements = allAchievements(),
                incrementStatistic = { error("increment failed") },
                triggerCriterion = { triggered = true },
            )
        } catch (_: IllegalStateException) {
            // 預期統計操作失敗會中止授予。
        }

        assertFalse(triggered)
    }

    /** 所有統計累加完成後只觸發一次進度條件。 */
    @Test
    fun `criterion triggers once after all increments`() {
        val operations = mutableListOf<String>()

        AchievementGrantExecutor.grant(
            achievements = allAchievements(),
            incrementStatistic = { statisticId -> operations += "increment:$statisticId" },
            triggerCriterion = { operations += "criterion" },
        )

        assertEquals("criterion", operations.last())
        assertEquals(1, operations.count { it == "criterion" })
        assertEquals(AchievementStatisticIds.ALL.size + 1, operations.size)
    }
}
