package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundSettlementHandPresentation
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** [RoundSettlementPresentationRequestFactory] 的手牌公開策略與玩家數測試。 */
class RoundSettlementPresentationRequestFactoryTest {
    private val config = RiichiRuleConfig()
    private val module = RiichiRuleModule("mahjongcraft:riichi", config)

    /** 一般流局應翻開聽牌者並蓋起未聽者。 */
    @Test
    fun `normal draw reveals tenpai hands and conceals noten hands`() {
        val state = tableState(4)
        val tenpaiPlayer = state.players.first()
        val request = RoundSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = state,
            module = module,
            reason = RiichiExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = setOf(tenpaiPlayer.id),
            revealedHands = listOf(RevealedHandSettlement(tenpaiPlayer.id, setOf(Tile.Numeric(Tile.Suit.Character, 1)))),
        )

        assertEquals(RoundSettlementHandPresentation.REVEAL_TENPAI, request.players.first().handPresentation)
        assertEquals(RoundSettlementHandPresentation.CONCEAL, request.players[1].handPresentation)
        assertEquals(tenpaiPlayer.hand.allTiles.map { it.id }, request.players.first().revealedHandTileIds)
        assertEquals(emptyList(), request.players[1].revealedHandTileIds)
    }

    /** 九種九牌只公開宣告者，其餘玩家必須蓋牌。 */
    @Test
    fun `abortive proof reveals declarer and conceals other hands`() {
        val state = tableState(4)
        val declarer = state.players[2]
        val request = RoundSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = state,
            module = module,
            reason = RiichiExhaustiveDrawReason.KyuushuKyuuhai,
            tenpaiPlayerIds = null,
            revealedHands = listOf(RevealedHandSettlement(declarer.id, emptySet())),
        )

        assertEquals(RoundSettlementHandPresentation.REVEAL_PROOF, request.players[2].handPresentation)
        request.players.filterNot { it.playerId == declarer.id }.forEach { player ->
            assertEquals(RoundSettlementHandPresentation.CONCEAL, player.handPresentation)
        }
    }

    /** Request 應保留規則實際提供的二至四人座位數，不補齊固定四列。 */
    @Test
    fun `request preserves actual player count`() {
        (2..4).forEach { playerCount ->
            val state = tableState(playerCount)
            val request = RoundSettlementPresentationRequestFactory.create(
                previousState = state,
                currentState = state,
                module = module,
                reason = RiichiExhaustiveDrawReason.SuufonRenda,
                tenpaiPlayerIds = null,
                revealedHands = emptyList(),
            )

            assertEquals(playerCount, request.players.size)
            request.players.forEach { player ->
                assertEquals(RoundSettlementHandPresentation.CONCEAL, player.handPresentation)
            }
        }
    }

    /** 建立指定玩家數、每人持有一張可追蹤測試牌的日麻桌況。 */
    private fun tableState(playerCount: Int) = FakeTableStateFactory.create(
        players = Wind.entries.take(playerCount).mapIndexed { index, wind ->
            FakeMahjongPlayerFactory.create(
                initialSeat = wind,
                hand = FakeHandFactory.create(listOf(Tile.Numeric(Tile.Suit.Character, index + 1))),
            ).copy(score = 25_000 - index * 1_000)
        },
        config = config,
    )
}
