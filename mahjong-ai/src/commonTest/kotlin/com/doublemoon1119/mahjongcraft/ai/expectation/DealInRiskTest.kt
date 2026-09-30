package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.m
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證放銃期望損失的彙整方式。 */
class DealInRiskTest {
    /** 一位確定聽牌、放銃損失 1000 點的對手；一萬到四萬的危險度依序為 0.1、0.2、0.3、0.4。 */
    private val opponentId = Uuid.random()
    private val risk = DealInRisk(
        threats = listOf(OpponentThreat(opponentId = opponentId, readyProbability = 1.0, lossOnDealIn = 1000.0)),
        danger = { _, tile -> (tile as Tile.Numeric).value / 10.0 },
    )
    private val tiles = listOf(m(4), m(2), m(1), m(3))

    /** 依損失由小到大取前幾張加總。 */
    @Test
    fun `safest losses add the smallest losses first`() {
        assertEquals(100.0 + 200.0, risk.safestLosses(tiles, count = 2), absoluteTolerance = 1e-9)
    }

    /** 需要打出的張數超過手牌時，超出的部分以平均損失計算。 */
    @Test
    fun `safest losses beyond the hand use the average loss`() {
        assertEquals(1000.0 + 2 * 250.0, risk.safestLosses(tiles, count = 6), absoluteTolerance = 1e-9)
    }

    /** 沒有列入防守計算的對手，或不需要打牌時，損失為 0。 */
    @Test
    fun `safest losses are zero without threats or turns`() {
        val noThreats = DealInRisk(threats = emptyList(), danger = { _, _ -> 1.0 })

        assertEquals(0.0, noThreats.safestLosses(tiles, count = 3))
        assertEquals(0.0, risk.safestLosses(tiles, count = 0))
    }
}
