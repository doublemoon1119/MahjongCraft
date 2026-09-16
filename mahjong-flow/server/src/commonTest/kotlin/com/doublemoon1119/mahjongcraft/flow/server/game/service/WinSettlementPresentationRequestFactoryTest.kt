package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

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

    /** 贏家結算給出的付款原因會帶進 request，沒有原因的玩家不出現。 */
    @Test
    fun `carries the payment reasons of the winner into the request`() {
        val state = fourPlayerState()
        val (winner, liable, discarder) = state.players

        val request = createRequest(
            state = state,
            responsiblePlayerId = discarder.id,
            resolutions = mapOf(winner.id to resolution(payments = listOf(discarder.id, liable.id), reasons = mapOf(liable.id to "test:liable"))),
        )

        assertEquals(mapOf(liable.id to "test:liable"), request.paymentReasonIdsByPlayerId)
    }

    /** 沒有任何付款原因時 request 也沒有。 */
    @Test
    fun `has no payment reasons for an ordinary win`() {
        val state = fourPlayerState()
        val (winner, _, discarder) = state.players

        val request = createRequest(
            state = state,
            responsiblePlayerId = discarder.id,
            resolutions = mapOf(winner.id to resolution(payments = listOf(discarder.id))),
        )

        assertEquals(emptyMap(), request.paymentReasonIdsByPlayerId)
    }

    /** 同一位玩家被兩位贏家標上不同原因時，取頭跳順位較前的贏家給出的原因。 */
    @Test
    fun `prefers the reason of the winner first in turn order from the discarder`() {
        val state = fourPlayerState()
        val (seat0, seat1, seat2, seat3) = state.players

        // 放銃者在座位 1：座位 2 先輪到，座位 0 最後；座位 3 是兩位贏家共同的責任者。
        val reasons = WinSettlementPresentationRequestFactory.mergePaymentReasons(
            state = state,
            responsiblePlayerId = seat1.id,
            resolutions = linkedMapOf(
                seat0.id to resolution(payments = listOf(seat1.id, seat3.id), reasons = mapOf(seat3.id to "test:later")),
                seat2.id to resolution(payments = listOf(seat1.id, seat3.id), reasons = mapOf(seat3.id to "test:earlier")),
            ),
        )

        assertEquals(mapOf(seat3.id to "test:earlier"), reasons)
    }

    /** 自摸沒有放銃者時，依結算的順序合併。 */
    @Test
    fun `keeps the resolution order without a responsible player`() {
        val state = fourPlayerState()
        val (seat0, seat1, _, seat3) = state.players

        val reasons = WinSettlementPresentationRequestFactory.mergePaymentReasons(
            state = state,
            responsiblePlayerId = null,
            resolutions = linkedMapOf(
                seat3.id to resolution(payments = listOf(seat1.id), reasons = mapOf(seat1.id to "test:first")),
                seat0.id to resolution(payments = listOf(seat1.id), reasons = mapOf(seat1.id to "test:second")),
            ),
        )

        assertEquals(mapOf(seat1.id to "test:first"), reasons)
    }

    /** 建立四人桌況。 */
    private fun fourPlayerState(): TableState = FakeTableStateFactory.create(
        players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(initialSeat = it) },
        config = config,
    )

    /** 以一般榮和呼叫 [WinSettlementPresentationRequestFactory.create]。 */
    private fun createRequest(
        state: TableState,
        responsiblePlayerId: Uuid?,
        resolutions: Map<Uuid, WinResolutionResult>,
    ) = WinSettlementPresentationRequestFactory.create(
        previousState = state,
        currentState = state,
        module = module,
        outcomeId = BuiltInRoundOutcomeIds.RON,
        isTsumo = responsiblePlayerId == null,
        winningTileId = Uuid.random(),
        responsiblePlayerId = responsiblePlayerId,
        resolutions = resolutions,
        detailResolverRegistry = detailResolverRegistry,
    )

    /** 建立每位付款者各付 1000 點的結算。 */
    private fun resolution(
        payments: List<Uuid>,
        reasons: Map<Uuid, String> = emptyMap(),
    ) = WinResolutionResult(
        settlement = WinSettlementResult(
            totalGained = payments.size * 1000,
            paymentsByPlayerId = payments.associateWith { 1000 },
            paymentReasonIdsByPlayerId = reasons,
        ),
        handValueResult = object : HandValueResult {},
    )
}
