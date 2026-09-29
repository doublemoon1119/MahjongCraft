package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.module
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.player
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.table
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證接近終局時把點數得失換算為名次得失。 */
class PlacementUtilityTest {
    /** 東家 30000、南家（自己）26000、西家 24000、北家 20000。 */
    private val players = listOf(
        player(Wind.EAST).copy(score = 30000),
        player(Wind.SOUTH).copy(score = 26000),
        player(Wind.WEST).copy(score = 24000),
        player(Wind.NORTH).copy(score = 20000),
    )
    private val selfId = players[1].id

    /** 原定最後一局時，超過第一名的和牌多一個名次的價值，掉到第三名的放銃多一個名次的損失。 */
    @Test
    fun `in the final round a place change is worth extra points`() {
        val finalRound = utility(sequenceIndex = 3, considersPlacement = true)

        assertEquals(5000.0 + STEP_POINTS, finalRound.gain(5000.0))
        assertEquals(1000.0, finalRound.gain(1000.0))
        assertEquals(3000.0 + STEP_POINTS, finalRound.loss(3000.0))
    }

    /** 還沒到原定最後一局，或等級不考慮名次時，價值就是點數。 */
    @Test
    fun `before the final round or without placement the value is the points`() {
        val early = utility(sequenceIndex = 1, considersPlacement = true)
        val disabled = utility(sequenceIndex = 3, considersPlacement = false)

        assertEquals(5000.0, early.gain(5000.0))
        assertEquals(5000.0, disabled.gain(5000.0))
        assertEquals(3000.0, disabled.loss(3000.0))
    }

    /** 東風戰第 [sequenceIndex] + 1 局、以南家為評估者的名次換算。 */
    private fun utility(sequenceIndex: Int, considersPlacement: Boolean): PlacementUtility = PlacementUtility.from(
        snapshot = snapshotAt(sequenceIndex),
        selfId = selfId,
        ranking = module.compareForMatchRanking(),
        considersPlacement = considersPlacement,
        stepPoints = STEP_POINTS,
    )

    /** 東風戰第 [sequenceIndex] + 1 局的快照。 */
    private fun snapshotAt(sequenceIndex: Int): TableStateSnapshot = table(players)
        .copy(
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            roundNumber = sequenceIndex + 1,
            roundPosition = MatchRoundPosition(sequenceIndex, Wind.EAST, sequenceIndex + 1),
        )
        .toSnapshot(setOf(selfId))

    /** 測試常數。 */
    private companion object {
        /** 名次每升降一位相當的點數。 */
        val STEP_POINTS: Int = ExpectationParameters.DEFAULT.placementStepPoints
    }
}
