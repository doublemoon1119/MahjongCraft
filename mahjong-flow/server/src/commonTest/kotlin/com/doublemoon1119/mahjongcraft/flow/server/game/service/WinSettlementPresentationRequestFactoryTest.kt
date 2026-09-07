package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證特殊 win-equivalent outcome 結算時，贏家手牌與副露的呈現分離。 */
class WinSettlementPresentationRequestFactoryTest {
    private val config = RiichiRuleConfig()
    private val module = RiichiRuleModule("mahjongcraft:riichi", config)
    private val detailResolverRegistry = WinSettlementDetailResolverRegistry()

    /**
     * 副露牌張只能出現在 [WinSettlementWinnerPresentation.melds]，絕不能同時混進
     * [WinSettlementWinnerPresentation.standingTileIds]——否則 renderer 會把同一組副露多畫一次在手牌裡。
     */
    @Test
    fun `special outcome hand tiles exclude meld tiles`() {
        val meldTiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }
        val standing = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5))
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(
                tiles = listOf(standing),
                melds = listOf(Meld(MeldType.PON, meldTiles, meldTiles.first(), RelativeDirection.Left)),
            ),
        )
        val state = FakeTableStateFactory.create(
            players = listOf(winner) + List(3) { FakeMahjongPlayerFactory.create() },
            config = config,
        )
        val outcome = ResolvedRoundOutcome(
            id = "mahjongcraft:test_outcome",
            settledTableState = state,
            beneficiaryPlayerIds = setOf(winner.id),
            scoreDeltas = state.players.associate { it.id to 0 },
            transitionDirective = RoundTransitionDirective.ADVANCE_DEALER,
            presentationClassification = RoundOutcomePresentationClassification.WIN_EQUIVALENT,
        )

        val request = WinSettlementPresentationRequestFactory.createSpecialOutcome(state, outcome, module, detailResolverRegistry)

        val winnerPresentation = request.winners.first()
        assertEquals(listOf(standing.id), winnerPresentation.standingTileIds)
        meldTiles.forEach { meldTile ->
            assertTrue(
                meldTile.id !in winnerPresentation.standingTileIds,
                "A meld tile must never be duplicated into the hand tile group.",
            )
        }
    }

    /**
     * 流局滿貫的 outcome id 必須透過 [WinSettlementDetailResolverRegistry] 正確分派到日麻樣板與役種
     * 欄位——這條路徑在遊戲內很難自然重現（需要真的打出流局滿貫），靠這個測試取代進遊戲驗證。
     */
    @Test
    fun `dispatches nagashi mangan special outcome through the registry`() {
        val registry = WinSettlementDetailResolverRegistry().apply { registerRiichiWinSettlementDetailResolver() }
        val winner = FakeMahjongPlayerFactory.create()
        val state = FakeTableStateFactory.create(
            players = listOf(winner) + List(3) { FakeMahjongPlayerFactory.create() },
            config = config,
        )
        val outcome = ResolvedRoundOutcome(
            id = BuiltInRoundOutcomeIds.NAGASHI_MANGAN,
            settledTableState = state,
            beneficiaryPlayerIds = setOf(winner.id),
            scoreDeltas = state.players.associate { it.id to 0 },
            transitionDirective = RoundTransitionDirective.ADVANCE_DEALER,
            presentationClassification = RoundOutcomePresentationClassification.WIN_EQUIVALENT,
        )

        val request = WinSettlementPresentationRequestFactory.createSpecialOutcome(state, outcome, module, registry)

        assertEquals(RiichiWinSettlementDetailResolver.TEMPLATE_KEY, request.templateKey)
        assertEquals(RiichiWinSettlementDetailResolver.YAKU_FIELD, request.winners.single().detailFields.single().id)
    }
}
