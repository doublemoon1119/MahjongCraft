package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [RoundPlayerActivity] 判斷整桌真人是否只有逾時代打的測試。 */
class RoundPlayerActivityTest {
    /** 兩位測試玩家。 */
    private val first = Uuid.random()
    private val second = Uuid.random()

    /** 每位真人都逾時過、沒有人主動操作時算無人操作。 */
    @Test
    fun `every human timing out without acting is inactive`() {
        val activity = RoundPlayerActivity().withTimedOut(first).withTimedOut(second)

        assertTrue(activity.isInactive(setOf(first, second)))
    }

    /** 有人主動操作過，即使也逾時過，就不算無人操作。 */
    @Test
    fun `any active human makes the round active`() {
        val activity = RoundPlayerActivity().withTimedOut(first).withTimedOut(second).withActive(second)

        assertFalse(activity.isInactive(setOf(first, second)))
    }

    /** 有真人這一局從未逾時，就不算無人操作。 */
    @Test
    fun `a human who never timed out keeps the round active`() {
        val activity = RoundPlayerActivity().withTimedOut(first)

        assertFalse(activity.isInactive(setOf(first, second)))
    }

    /** 沒有真人時不算無人操作。 */
    @Test
    fun `no human players is never inactive`() {
        assertFalse(RoundPlayerActivity().isInactive(emptySet()))
    }
}
