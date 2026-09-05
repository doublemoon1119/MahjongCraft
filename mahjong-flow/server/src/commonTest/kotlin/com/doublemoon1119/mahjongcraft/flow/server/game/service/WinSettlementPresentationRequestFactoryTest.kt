package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
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

/** 驗證胡牌結算中的日麻翻符顯示政策，以及贏家手牌與副露的呈現分離。 */
class WinSettlementPresentationRequestFactoryTest {
    private val config = RiichiRuleConfig()
    private val module = RiichiRuleModule("mahjongcraft:riichi", config)

    /** 未達滿貫且具有權威符數時應同時顯示翻數與符數。 */
    @Test
    fun includesFuWhenAvailable() {
        val value = WinSettlementPresentationRequestFactory.riichiHanFuValue(totalHan = 3, totalFu = 30)

        assertEquals(WinSettlementTranslationKeys.HAN_FU, value.translationKey)
        assertEquals(listOf("3", "30"), value.arguments)
    }

    /** 滿貫以上的符數為零時不得顯示不存在的符數。 */
    @Test
    fun omitsFuWhenUnavailable() {
        val value = WinSettlementPresentationRequestFactory.riichiHanFuValue(totalHan = 5, totalFu = 0)

        assertEquals(WinSettlementTranslationKeys.HAN, value.translationKey)
        assertEquals(listOf("5"), value.arguments)
    }

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

        val request = WinSettlementPresentationRequestFactory.createSpecialOutcome(state, outcome, module)

        val winnerPresentation = request.winners.first()
        assertEquals(listOf(standing.id), winnerPresentation.standingTileIds)
        meldTiles.forEach { meldTile ->
            assertTrue(
                meldTile.id !in winnerPresentation.standingTileIds,
                "A meld tile must never be duplicated into the hand tile group.",
            )
        }
    }
}
