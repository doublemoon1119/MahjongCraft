package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundOutcomeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** 驗證牌譜回放的胡牌特效打在哪幾張牌上。 */
class HistoryReplayWinEffectsTest {
    /** 每次胡牌只在自己那一步放特效，不論有沒有攤牌。同一步共用的和牌張只放一次。 */
    @Test
    fun `each win plays on its own step and winning tile`() {
        val transactions = listOf(
            transaction(index = 0, outcome(winningTile = 9), outcome(winningTile = 9)),
            transaction(index = 1, outcome(winningTile = null)),
            transaction(index = 2, outcome(winningTile = 4)),
        )
        assertEquals(listOf(9), HistoryReplayWinEffects.winningTiles(transactions, step = 1))
        assertEquals(emptyList(), HistoryReplayWinEffects.winningTiles(transactions, step = 2))
        assertEquals(listOf(4), HistoryReplayWinEffects.winningTiles(transactions, step = 3))
    }

    /** 種子由局、步數與牌決定。 */
    @Test
    fun `seeds are stable and differ between steps`() {
        assertEquals(HistoryReplayWinEffects.seed(1, 5, 9), HistoryReplayWinEffects.seed(1, 5, 9))
        assertNotEquals(HistoryReplayWinEffects.seed(1, 5, 9), HistoryReplayWinEffects.seed(1, 6, 9))
        assertNotEquals(HistoryReplayWinEffects.seed(1, 5, 9), HistoryReplayWinEffects.seed(2, 5, 9))
    }

    /**
     * 建立一筆交易。
     *
     * @param index 交易索引。
     * @param outcomes 交易中的結算。
     * @return 交易。
     */
    private fun transaction(index: Int, vararg outcomes: HistoryRoundOutcomeDto): HistoryReplayTransactionDto = HistoryReplayTransactionDto(
        index = index,
        occurredAtEpochMillis = 0L,
        isOpening = false,
        declaredTileCountAfter = 0,
        facts = outcomes.map { HistoryReplayFactDto.Completion(typeKey = "test:win", outcome = it) },
    )

    /**
     * 建立一筆不攤牌的胡牌結算。
     *
     * @param winningTile 和牌張。
     * @return 結算。
     */
    private fun outcome(winningTile: Int?): HistoryRoundOutcomeDto = HistoryRoundOutcomeDto(
        reasonId = "test:win",
        beneficiarySeats = listOf(0),
        scoresBySeat = emptyMap(),
        classification = null,
        responsibleSeats = emptyList(),
        transitionDirective = null,
        nextDealerSeat = null,
        scoreChangesBySeat = emptyMap(),
        winnerDetails = emptyList(),
        hasEarlierWinSettlement = false,
        winningTile = winningTile,
    )
}
