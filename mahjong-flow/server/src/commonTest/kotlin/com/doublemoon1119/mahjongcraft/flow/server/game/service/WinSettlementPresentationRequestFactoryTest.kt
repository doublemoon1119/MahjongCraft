package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.bundled.BundledRiichiExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementWinnerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
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

/** 驗證一般和牌與視同和牌結果的手牌、付款原因及 AI 標記轉換。 */
class WinSettlementPresentationRequestFactoryTest {
    /** 各測試使用的日麻設定。 */
    private val config = RiichiRuleConfig()

    /** 提供牌序與結算政策的日麻模組。 */
    private val module = RiichiRuleModule("mahjongcraft:riichi", config)

    /** 未額外註冊規則明細的解析器集合。 */
    private val detailResolverRegistry = WinSettlementDetailResolverRegistry()

    /**
     * 驗證副露牌只出現在 [WinSettlementWinnerPresentation.melds]，
     * 不重複加入 [WinSettlementWinnerPresentation.standingTileIds]。
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

        val request = WinSettlementPresentationRequestFactory.createSpecialOutcome(state, outcome, module, emptySet(), detailResolverRegistry)

        val winnerPresentation = request.winners.first()
        assertEquals(listOf(standing.id), winnerPresentation.standingTileIds)
        assertTrue(winnerPresentation.detailFields.isEmpty(), "An unknown special outcome may omit optional detail fields")
        meldTiles.forEach { meldTile ->
            assertTrue(
                meldTile.id !in winnerPresentation.standingTileIds,
                "A meld tile must never be duplicated into the hand tile group.",
            )
        }
    }

    /** 詳情解析器不支援特殊結果時，factory 仍保留共用結算資料並交付空詳情欄位。 */
    @Test
    fun `special outcome with resolver returning null keeps settlement data`() {
        val state = fourPlayerState()
        val winner = state.players.first()
        val outcome = ResolvedRoundOutcome(
            id = "test:unsupported_special_outcome",
            settledTableState = state,
            beneficiaryPlayerIds = setOf(winner.id),
            scoreDeltas = state.players.associate { it.id to 0 },
            transitionDirective = RoundTransitionDirective.ADVANCE_DEALER,
            presentationClassification = RoundOutcomePresentationClassification.WIN_EQUIVALENT,
        )
        val registry = WinSettlementDetailResolverRegistry().apply {
            register(
                WinSettlementDetailResolverDefinition(
                    ruleModuleId = module.id,
                    resolver = object : WinSettlementDetailResolver {
                        override fun resolve(state: TableState, handValue: HandValueResult): List<WinSettlementDetailField> = emptyList()

                        override fun resolveSpecialOutcome(
                            state: TableState,
                            outcome: ResolvedRoundOutcome,
                        ): List<WinSettlementDetailField>? = null
                    },
                ),
            )
        }

        val request = WinSettlementPresentationRequestFactory.createSpecialOutcome(
            previousState = state,
            outcome = outcome,
            module = module,
            aiPlayerIds = emptySet(),
            detailResolverRegistry = registry,
        )

        assertEquals(winner.id, request.winners.single().playerId)
        assertEquals(emptyList(), request.winners.single().detailFields)
        assertEquals(state.players.size, request.ranking.players.size)
    }

    /** 驗證一般和牌要求中的立牌依規則牌序排列，且不包含獨立的和牌張。 */
    @Test
    fun `win hand tiles follow the rule tile order`() {
        val east = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val nineCharacters = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9))
        val twoDots = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
        val oneCharacters = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(east, nineCharacters, twoDots, oneCharacters), lastDrawn = winningTile),
        )
        val state = FakeTableStateFactory.create(
            players = listOf(winner) + listOf(Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(initialSeat = it) },
            config = config,
        )

        val request = WinSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = state,
            module = module,
            aiPlayerIds = emptySet(),
            outcomeId = BuiltInRoundOutcomeIds.TSUMO,
            isTsumo = true,
            winningTileId = winningTile.id,
            responsiblePlayerId = null,
            resolutions = mapOf(winner.id to resolution(payments = state.players.drop(1).map { it.id })),
            detailResolverRegistry = detailResolverRegistry,
        )

        assertEquals(
            listOf(oneCharacters.id, nineCharacters.id, twoDots.id, east.id),
            request.winners.single().standingTileIds,
        )
    }

    /** 特殊和局結果的立牌同樣依規則牌序排好。 */
    @Test
    fun `special outcome hand tiles follow the rule tile order`() {
        val white = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val threeBamboo = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 3))
        val sevenDots = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 7))
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(white, threeBamboo, sevenDots)),
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

        val request = WinSettlementPresentationRequestFactory.createSpecialOutcome(state, outcome, module, emptySet(), detailResolverRegistry)

        assertEquals(listOf(sevenDots.id, threeBamboo.id, white.id), request.winners.single().standingTileIds)
    }

    /** 驗證流局滿貫透過 [WinSettlementDetailResolverRegistry] 取得日麻規則 ID 與役種欄位。 */
    @Test
    fun `dispatches nagashi mangan special outcome through the registry`() {
        val registry = WinSettlementDetailResolverRegistry().apply { BundledRiichiExtension.registerWinSettlementDetailResolvers(this) }
        val winner = FakeMahjongPlayerFactory.create()
        val state = FakeTableStateFactory.create(
            players = listOf(winner) + List(3) { FakeMahjongPlayerFactory.create() },
            config = config,
        )
        val outcome = ResolvedRoundOutcome(
            id = RiichiRoundOutcomeIds.NAGASHI_MANGAN,
            settledTableState = state,
            beneficiaryPlayerIds = setOf(winner.id),
            scoreDeltas = state.players.associate { it.id to 0 },
            transitionDirective = RoundTransitionDirective.ADVANCE_DEALER,
            presentationClassification = RoundOutcomePresentationClassification.WIN_EQUIVALENT,
        )

        val request = WinSettlementPresentationRequestFactory.createSpecialOutcome(state, outcome, module, emptySet(), registry)

        assertEquals(module.id, request.ruleModuleId)
        assertEquals(RiichiWinSettlementIds.YAKU_FIELD, request.winners.single().detailFields.single().id)
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

    /** 未註冊規則詳情時仍保留贏家、分數排行與結算流程，只省略規則專屬欄位。 */
    @Test
    fun `ordinary win remains complete without a detail resolver`() {
        val state = fourPlayerState()
        val (winner, _, discarder, _) = state.players

        val request = createRequest(
            state = state,
            responsiblePlayerId = discarder.id,
            resolutions = mapOf(winner.id to resolution(payments = listOf(discarder.id))),
        )

        assertEquals(winner.id, request.winners.single().playerId)
        assertEquals(emptyList(), request.winners.single().detailFields)
        assertEquals(state.players.size, request.ranking.players.size)
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

    /** 排名只把傳入的 AI 玩家標成 AI。 */
    @Test
    fun `ranking marks only the given players as ai`() {
        val state = fourPlayerState()
        val winner = state.players[0]
        val aiPlayer = state.players[2]
        val request = createRequest(
            state = state,
            responsiblePlayerId = state.players[1].id,
            resolutions = mapOf(winner.id to resolution(payments = listOf(state.players[1].id))),
            aiPlayerIds = setOf(aiPlayer.id),
        )

        assertEquals(listOf(aiPlayer.id), request.ranking.players.filter { it.isAi }.map { it.playerId })
    }

    /**
     * 建立四人測試桌況。
     *
     * @return 使用四個初始風位的測試桌況。
     */
    private fun fourPlayerState(): TableState = FakeTableStateFactory.create(
        players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(initialSeat = it) },
        config = config,
    )

    /**
     * 使用榮和結果 ID 建立測試結算要求，自摸旗標則依是否有放銃者決定。
     *
     * @param state 結算前的桌況。
     * @param responsiblePlayerId 放銃者；為 null 時建立自摸呈現請求。
     * @param resolutions 每位贏家的權威結算結果。
     * @param aiPlayerIds AI 玩家識別碼。
     * @return 建立的胡牌結算呈現請求。
     */
    private fun createRequest(
        state: TableState,
        responsiblePlayerId: Uuid?,
        resolutions: Map<Uuid, WinResolutionResult>,
        aiPlayerIds: Set<Uuid> = emptySet(),
    ) = WinSettlementPresentationRequestFactory.create(
        previousState = state,
        currentState = state,
        module = module,
        aiPlayerIds = aiPlayerIds,
        outcomeId = BuiltInRoundOutcomeIds.RON,
        isTsumo = responsiblePlayerId == null,
        winningTileId = Uuid.random(),
        responsiblePlayerId = responsiblePlayerId,
        resolutions = resolutions,
        detailResolverRegistry = detailResolverRegistry,
    )

    /**
     * 建立每位付款者各付 1000 點的結算。
     *
     * @param payments 付款者識別碼。
     * @param reasons 付款者對應的結算原因識別碼。
     * @return 測試用的權威胡牌結算。
     */
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
