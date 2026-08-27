package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SettledWinPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationWinner
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementWinnerPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.uuid.Uuid

/** [WinPresentationHandoff] 的關聯鍵驗證與殘留清理測試。 */
class WinPresentationHandoffTest {
    private val handoff = WinPresentationHandoff()
    private val gameId = Uuid.random()
    private val firstWinnerId = Uuid.random()
    private val secondWinnerId = Uuid.random()

    /** 贏家相符時取回原本暫存的內容。 */
    @Test
    fun `take returns the staged presentation when the winners match`() {
        val staged = presentationFor(firstWinnerId)
        handoff.stage(gameId, staged)

        assertSame(staged, handoff.take(gameId, setOf(firstWinnerId)))
    }

    /** 取走後即清空，同一筆不會被取第二次。 */
    @Test
    fun `take clears the staged presentation`() {
        handoff.stage(gameId, presentationFor(firstWinnerId))
        handoff.take(gameId, setOf(firstWinnerId))

        assertNull(handoff.take(gameId, setOf(firstWinnerId)))
    }

    /**
     * 第一次胡牌暫存後收斂流程失敗（沒有人取走），第二次胡牌**不得**拿到第一次的內容。
     *
     * 這是關聯鍵存在的理由：贏家不符就視為殘留資料，一律丟棄。
     */
    @Test
    fun `a later win never receives a stale presentation from a failed earlier win`() {
        handoff.stage(gameId, presentationFor(firstWinnerId))

        assertNull(
            handoff.take(gameId, setOf(secondWinnerId)),
            "The second win must not receive the first win's presentation.",
        )
        assertNull(
            handoff.take(gameId, setOf(firstWinnerId)),
            "The stale entry must also be cleared, not left for a third win to pick up.",
        )
    }

    /** [WinPresentationHandoff.discard] 清掉尚未取走的內容。 */
    @Test
    fun `discard clears a staged presentation`() {
        handoff.stage(gameId, presentationFor(firstWinnerId))
        handoff.discard(gameId)

        assertNull(handoff.take(gameId, setOf(firstWinnerId)))
    }

    /** 不同對局互不影響。 */
    @Test
    fun `staging is scoped per game`() {
        val otherGameId = Uuid.random()
        val staged = presentationFor(firstWinnerId)
        handoff.stage(gameId, staged)

        assertNull(handoff.take(otherGameId, setOf(firstWinnerId)))
        assertEquals(staged, handoff.take(gameId, setOf(firstWinnerId)))
    }

    /** 建立最小可用的測試用呈現內容。 */
    private fun presentationFor(winnerId: Uuid): SettledWinPresentation {
        val winningTileId = Uuid.random()
        return SettledWinPresentation(
            winnerPlayerIds = setOf(winnerId),
            celebration = WinCelebrationRequest(
                winningTileId = winningTileId,
                isTsumo = true,
                winners = listOf(WinCelebrationWinner(0, null)),
            ),
            settlement = WinSettlementPresentationRequest(
                outcomeId = "mahjongcraft:tsumo",
                templateKey = "mahjongcraft:riichi",
                isTsumo = true,
                winners = listOf(
                    WinSettlementWinnerPresentation(
                        playerId = winnerId,
                        seatIndex = 0,
                        responsiblePlayerId = null,
                        totalScore = 8000,
                        handTileIds = emptyList(),
                        melds = emptyList(),
                        winningTileId = winningTileId,
                        detailFields = emptyList(),
                    ),
                ),
                ranking = ScoreRankingPresentation(
                    players = listOf(
                        ScoreRankingPlayer(winnerId, 0, isAi = false, previousScore = 25_000, currentScore = 33_000, previousRank = 1, currentRank = 1),
                    ),
                ),
            ),
        )
    }
}
