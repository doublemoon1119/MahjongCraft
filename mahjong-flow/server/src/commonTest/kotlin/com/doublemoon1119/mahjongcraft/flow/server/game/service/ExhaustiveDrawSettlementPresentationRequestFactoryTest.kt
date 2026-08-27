package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInExhaustiveDrawSettlementStatusIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementHandPresentation
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [ExhaustiveDrawSettlementPresentationRequestFactory] 的手牌公開策略與玩家數測試。 */
class ExhaustiveDrawSettlementPresentationRequestFactoryTest {
    private val config = RiichiRuleConfig()
    private val module = RiichiRuleModule("mahjongcraft:riichi", config)

    /** 一般流局應翻開聽牌者並蓋起未聽者。 */
    @Test
    fun `normal draw reveals tenpai hands and conceals noten hands`() {
        val state = tableState(4)
        val tenpaiPlayer = state.players.first()
        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = state,
            module = module,
            reason = RiichiExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = setOf(tenpaiPlayer.id),
            revealedHands = listOf(RevealedHandSettlement(tenpaiPlayer.id, setOf(Tile.Numeric(Tile.Suit.Character, 1)))),
        )

        assertEquals(ExhaustiveDrawSettlementHandPresentation.REVEAL_TENPAI, request.players.first().handPresentation)
        assertEquals(ExhaustiveDrawSettlementHandPresentation.CONCEAL, request.players[1].handPresentation)
        assertEquals(tenpaiPlayer.hand.allTiles.map { it.id }, request.players.first().revealedHandTileIds)
        assertEquals(emptyList(), request.players[1].revealedHandTileIds)
    }

    /** 九種九牌只公開宣告者，其餘玩家必須蓋牌。 */
    @Test
    fun `abortive proof reveals declarer and conceals other hands`() {
        val state = tableState(4)
        val declarer = state.players[2]
        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = state,
            module = module,
            reason = RiichiExhaustiveDrawReason.KyuushuKyuuhai,
            tenpaiPlayerIds = null,
            revealedHands = listOf(RevealedHandSettlement(declarer.id, emptySet())),
        )

        assertEquals(ExhaustiveDrawSettlementHandPresentation.REVEAL_PROOF, request.players[2].handPresentation)
        request.players.filterNot { it.ranking.playerId == declarer.id }.forEach { player ->
            assertEquals(ExhaustiveDrawSettlementHandPresentation.CONCEAL, player.handPresentation)
        }
    }

    /** Request 應保留規則實際提供的二至四人座位數，不補齊固定四列。 */
    @Test
    fun `request preserves actual player count`() {
        (2..4).forEach { playerCount ->
            val state = tableState(playerCount)
            val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
                previousState = state,
                currentState = state,
                module = module,
                reason = RiichiExhaustiveDrawReason.SuufonRenda,
                tenpaiPlayerIds = null,
                revealedHands = emptyList(),
            )

            assertEquals(playerCount, request.players.size)
            request.players.forEach { player ->
                assertEquals(ExhaustiveDrawSettlementHandPresentation.CONCEAL, player.handPresentation)
            }
        }
    }

    /**
     * 蓋牌範圍只能是立牌，**絕不包含副露**：吃、碰、明槓、暗槓本來就是公開資訊，蓋起來既不合規則，
     * 也會摧毀既有的牌面、橫置方向與加槓疊牌版面。
     */
    @Test
    fun `conceal targets standing tiles only and never melds`() {
        val meldTiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }
        val standing = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5))
        val withMeld = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(
                tiles = listOf(standing),
                melds = listOf(Meld(MeldType.PON, meldTiles, meldTiles.first(), RelativeDirection.Left)),
            ),
        )
        val state = FakeTableStateFactory.create(
            players = listOf(withMeld) + List(3) { FakeMahjongPlayerFactory.create() },
            config = config,
        )

        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = state,
            module = module,
            reason = RiichiExhaustiveDrawReason.SuufonRenda,
            tenpaiPlayerIds = null,
            revealedHands = emptyList(),
        )

        assertEquals(listOf(standing.id), request.players.first().handTileIds)
        meldTiles.forEach { meldTile ->
            assertTrue(
                meldTile.id !in request.players.first().handTileIds,
                "A meld tile must never be scheduled for the exhaustive-draw conceal animation.",
            )
        }
    }

    /**
     * 本局已胡牌退場的玩家不參與流局收尾：手牌在他胡牌時就收好了，不能再排一次動畫，也不該被標成
     * 不聽——他根本沒有參與這次流局。分數排行那一列仍然保留。
     */
    @Test
    fun `a finished player keeps its ranking row but gets no conceal animation or status`() {
        val base = tableState(4)
        val finished = base.players.last()
        val state = base.copy(finishedPlayerIds = setOf(finished.id))

        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = base,
            currentState = state,
            module = module,
            reason = RiichiExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = setOf(base.players.first().id),
            revealedHands = emptyList(),
        )

        val finishedRow = request.players.first { it.ranking.playerId == finished.id }
        assertEquals(emptyList(), finishedRow.handTileIds, "A finished player's tiles are already concealed.")
        assertEquals(null, finishedRow.statusId, "A finished player is neither tenpai nor noten.")
        assertEquals(4, request.players.size, "The score ranking still lists everyone.")
        assertEquals(
            BuiltInExhaustiveDrawSettlementStatusIds.NOTEN,
            request.players.first { it.ranking.playerId == base.players[1].id }.statusId,
            "Players still in the round are judged as usual.",
        )
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
