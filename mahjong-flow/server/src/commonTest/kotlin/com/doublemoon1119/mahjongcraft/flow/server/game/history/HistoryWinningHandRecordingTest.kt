package com.doublemoon1119.mahjongcraft.flow.server.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinningHand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementWinnerPresentation
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證權威贏家手牌描述會被複製到歷史詳情。 */
class HistoryWinningHandRecordingTest {
    /** 自摸贏家保留排序後的立牌參照與獨立和牌參照。 */
    @Test
    fun `tsumo recording preserves the authoritative winning hand descriptor`() {
        val winner = Uuid.random()
        val standing = listOf(Uuid.random(), Uuid.random(), Uuid.random())
        val winning = Uuid.random()

        val details = winSettlementHistoryDetails(
            request(
                outcomeId = "mahjongcraft:tsumo",
                winners = listOf(winnerPresentation(winner, 0, standing, winning)),
            ),
            "mahjongcraft:test",
            WinSettlementDetailResolverRegistry(),
        )

        assertEquals(HistoryWinningHand(standing, winning), details.single().hand)
    }

    /** 多家榮和保留各自不同的立牌手牌，同時共用局外和牌參照。 */
    @Test
    fun `multiple ron winners preserve distinct hands and shared winning tile`() {
        val first = Uuid.random()
        val second = Uuid.random()
        val winning = Uuid.random()
        val firstStanding = listOf(Uuid.random(), Uuid.random())
        val secondStanding = listOf(Uuid.random(), Uuid.random(), Uuid.random())

        val details = winSettlementHistoryDetails(
            request(
                outcomeId = "mahjongcraft:ron",
                winners = listOf(
                    winnerPresentation(first, 0, firstStanding, winning),
                    winnerPresentation(second, 1, secondStanding, winning),
                ),
            ),
            "mahjongcraft:test",
            WinSettlementDetailResolverRegistry(),
        )

        assertEquals(firstStanding, details[0].hand?.standingTileIds)
        assertEquals(secondStanding, details[1].hand?.standingTileIds)
        assertEquals(winning, details[0].hand?.winningTileId)
        assertEquals(winning, details[1].hand?.winningTileId)
    }

    /** 特殊等價胡牌結果不偽造和牌張。 */
    @Test
    fun `special outcome preserves standing tiles and null winning tile`() {
        val winner = Uuid.random()
        val standing = listOf(Uuid.random(), Uuid.random())

        val details = winSettlementHistoryDetails(
            request(
                outcomeId = "mahjongcraft:special",
                winners = listOf(winnerPresentation(winner, 0, standing, null)),
            ),
            "mahjongcraft:test",
            WinSettlementDetailResolverRegistry(),
        )

        assertEquals(standing, details.single().hand?.standingTileIds)
        assertNull(details.single().hand?.winningTileId)
    }

    /** 由權威贏家呈現資料建立最小結算要求。
     * @param outcomeId 結算原因識別碼。
     * @param winners 權威贏家呈現資料。
     * @return 可轉換為歷史詳情的結算要求。
     */
    private fun request(
        outcomeId: String,
        winners: List<WinSettlementWinnerPresentation>,
    ): WinSettlementPresentationRequest {
        val rankingPlayers = winners.map { winner ->
            ScoreRankingPlayer(winner.playerId, winner.seatIndex, false, 25000, 25000, winner.seatIndex + 1, winner.seatIndex + 1)
        }
        return WinSettlementPresentationRequest(
            outcomeId = outcomeId,
            templateKey = "mahjongcraft:test",
            isTsumo = outcomeId.endsWith("tsumo"),
            winners = winners,
            ranking = ScoreRankingPresentation(rankingPlayers),
        )
    }

    /** 建立一位含精確排序立牌與獨立和牌參照的贏家。
     * @param playerId 贏家玩家 UUID。
     * @param seatIndex 贏家座位索引。
     * @param standing 排序後且不含和牌張的立牌 UUID。
     * @param winning 獨立和牌 UUID；特殊結算時為 null。
     * @return 權威贏家呈現資料。
     */
    private fun winnerPresentation(
        playerId: Uuid,
        seatIndex: Int,
        standing: List<Uuid>,
        winning: Uuid?,
    ): WinSettlementWinnerPresentation = WinSettlementWinnerPresentation(
        playerId = playerId,
        seatIndex = seatIndex,
        responsiblePlayerId = null,
        totalScore = 1000,
        standingTileIds = standing,
        melds = emptyList(),
        winningTileId = winning,
        detailFields = emptyList(),
    )
}
