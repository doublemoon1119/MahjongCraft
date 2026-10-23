package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.runAsSingleWrite
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionContext
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionDecision
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionPolicy
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/**
 * 驗證流局用例、回合摘要與換局用例會沿用規則模組提供的莊家推進決策。
 *
 * 測試使用委派包裝的日麻規則模組，只覆寫 [MahjongRuleModule.resolveRoundTransition]；
 * 其餘合法動作、流局結算與發牌邏輯仍由日麻模組提供。
 */
class RoundTransitionFlowTest {

    /**
     * 建立指定下一任莊家與換位的完整流局用例測試環境。
     *
     * @param transitionDirective 依本局結果決定規則回傳的下一任莊家。
     * @param seatOrder 依本局結算後的桌況決定下一局的座位順序；null 表示規則不換位。
     */
    private class Fixtures(
        transitionDirective: (RoundTransitionContext) -> RoundTransitionDirective,
        seatOrder: ((TableState) -> List<Uuid>)? = null,
    ) {
        val gameId = Uuid.random()
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(RiichiRuleConfig::class, "test:round-transition") { config, id ->
                    TransitionOverrideRuleModule(
                        delegate = RiichiRuleModule(id, config),
                        transitionDirective = transitionDirective,
                        seatOrder = seatOrder,
                    )
                },
            )
        }
        private val snapshotRepo = FakeGameSnapshotRepository()
        private val snapshotSynchronizer = GameSnapshotSynchronizer(
            gameRepo,
            snapshotRepo,
            GameVisibilityPolicyImpl(moduleRegistry),
        )
        private val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        private val handSortPreferenceStore = HandSortPreferenceStore()

        val exhaustiveDrawUseCase = DeclareExhaustiveDrawUseCase(
            gameRepo,
            moduleRegistry,
            snapshotSynchronizer,
            eventPublisher,
        )
        val abortiveDrawUseCase = DeclareAbortiveDrawUseCase(
            gameRepo,
            moduleRegistry,
            snapshotSynchronizer,
            eventPublisher,
        )
        val advanceRoundUseCase = AdvanceRoundUseCase(
            gameRepo,
            moduleRegistry,
            snapshotSynchronizer,
            handSortPreferenceStore,
            eventPublisher,
            presentationPublisher,
        )
    }

    /** 以委派保留日麻規則，只替測試指定本局結束後的下一任莊家與換位。 */
    private class TransitionOverrideRuleModule(
        private val delegate: RiichiRuleModule,
        private val transitionDirective: (RoundTransitionContext) -> RoundTransitionDirective,
        private val seatOrder: ((TableState) -> List<Uuid>)?,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate {
        override fun resolveRoundTransition(
            context: RoundTransitionContext,
        ): RoundTransitionDirective = transitionDirective(context)

        override fun createMatchProgressionPolicy(): MatchProgressionPolicy {
            val base = delegate.createMatchProgressionPolicy()
            val order = seatOrder ?: return base
            return object : MatchProgressionPolicy {
                override fun decide(context: MatchProgressionContext): MatchProgressionDecision = when (val decision = base.decide(context)) {
                    is MatchProgressionDecision.ContinueMatch -> decision.copy(seatOrder = order(context.tableState))
                    is MatchProgressionDecision.EndMatch -> decision
                }
            }
        }

        override fun declareExhaustiveDraw(tableState: TableState): ExhaustiveDrawSettlementResult = ExhaustiveDrawSettlementResult(
            reason = RiichiExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = tableState.players.drop(1).take(1).mapTo(mutableSetOf()) { it.id },
            stickPotCollectorPlayerIds = emptySet(),
            scoreDeltas = emptyMap(),
        )
    }

    /** 驗證一般流局有非莊家聽牌時，摘要仍採用規則指定的連莊決策，並能實際進入下一局。 */
    @Test
    fun `exhaustive draw preserves rule transition and advances through use case`() = runTest {
        val fixtures = Fixtures({ RoundTransitionDirective.RepeatDealer })
        val table = startedTable(fixtures.gameId, currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.exhaustiveDrawUseCase(fixtures.gameId)

        assertIs<Outcome.Success<*>>(result)
        val completed = assertNotNull(fixtures.gameRepo.getGame(fixtures.gameId))
        assertEquals(RoundTransitionDirective.RepeatDealer, completed.roundCompletion?.transitionDirective)
        assertEquals(table.players[1].id, completed.roundCompletion?.beneficiaryPlayerIds?.single())

        val advanceResult = fixtures.advanceRoundUseCase(fixtures.gameId)

        assertIs<Outcome.Success<*>>(advanceResult)
        val next = assertNotNull(fixtures.gameRepo.getTableState(fixtures.gameId))
        assertEquals(table.roundNumber, next.roundNumber)
        assertEquals(table.comboCount + 1, next.comboCount)
    }

    /** 驗證九種九牌會採用規則指定的過莊決策，並能由實際換局用例開出下一局。 */
    @Test
    fun `abortive draw preserves rule transition and advances through use case`() = runTest {
        val fixtures = Fixtures({ RoundTransitionDirective.PassToNextSeat })
        val playerId = Uuid.random()
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(
                tiles = listOf(
                    Tile.Numeric(Tile.Suit.Character, 1),
                    Tile.Numeric(Tile.Suit.Character, 9),
                    Tile.Numeric(Tile.Suit.Dot, 1),
                    Tile.Numeric(Tile.Suit.Dot, 9),
                    Tile.Numeric(Tile.Suit.Bamboo, 1),
                    Tile.Numeric(Tile.Suit.Bamboo, 9),
                    Tile.Honor.East,
                    Tile.Honor.South,
                    Tile.Numeric(Tile.Suit.Character, 4),
                    Tile.Numeric(Tile.Suit.Character, 5),
                    Tile.Numeric(Tile.Suit.Dot, 4),
                    Tile.Numeric(Tile.Suit.Dot, 5),
                    Tile.Numeric(Tile.Suit.Bamboo, 4),
                ).map { FakeIdentifiedTileFactory.create(it) },
                lastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.West),
            ),
        )
        val table = FakeTableStateFactory.create(
            id = fixtures.gameId,
            players = listOf(declarer) + listOf(Wind.SOUTH, Wind.WEST, Wind.NORTH).map { seat ->
                FakeMahjongPlayerFactory.create(initialSeat = seat)
            },
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.gameRepo.runAsSingleWrite(fixtures.gameId) {
            fixtures.abortiveDrawUseCase(
                gameId = fixtures.gameId,
                playerId = playerId,
                reason = RiichiExhaustiveDrawReason.KyuushuKyuuhai,
            )
        }

        assertIs<Outcome.Success<*>>(result)
        val completed = assertNotNull(fixtures.gameRepo.getGame(fixtures.gameId))
        assertEquals(RoundTransitionDirective.PassToNextSeat, completed.roundCompletion?.transitionDirective)
        assertEquals(RoundCompletionClassification.ABORTIVE_DRAW, completed.roundCompletion?.classification)

        val advanceResult = fixtures.advanceRoundUseCase(fixtures.gameId)

        assertIs<Outcome.Success<*>>(advanceResult)
        val next = assertNotNull(fixtures.gameRepo.getTableState(fixtures.gameId))
        assertEquals(table.roundNumber + 1, next.roundNumber)
        // 日麻的流局本場累加不因過莊改變，這裡只覆寫莊家去留。
        assertEquals(table.comboCount + 1, next.comboCount)
    }

    /** 規則指定的玩家成為下一局的莊家，不是莊家的下家。 */
    @Test
    fun `a rule can pass the deal to a chosen player`() = runTest {
        val fixtures = Fixtures({ context -> RoundTransitionDirective.PassTo(context.tableState.players[2].id) })
        val table = startedTable(fixtures.gameId, currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)
        assertIs<Outcome.Success<*>>(fixtures.exhaustiveDrawUseCase(fixtures.gameId))

        assertIs<Outcome.Success<*>>(fixtures.advanceRoundUseCase(fixtures.gameId))

        val next = assertNotNull(fixtures.gameRepo.getTableState(fixtures.gameId))
        assertEquals(table.players[2].id, next.dealerPlayerId)
        assertEquals(table.roundNumber + 1, next.roundNumber)
    }

    /** 規則把莊家交給現任莊家自己時視為賽程錯誤，不開下一局。 */
    @Test
    fun `passing the deal to the current dealer is rejected`() = runTest {
        val fixtures = Fixtures({ context -> RoundTransitionDirective.PassTo(context.tableState.dealerPlayerId) })
        val table = startedTable(fixtures.gameId, currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)
        assertIs<Outcome.Success<*>>(fixtures.exhaustiveDrawUseCase(fixtures.gameId))

        val result = fixtures.advanceRoundUseCase(fixtures.gameId)

        assertIs<GameError.InvalidMatchProgression>(assertIs<Outcome.Error<GameError>>(result).error)
        assertEquals(table.roundNumber, fixtures.gameRepo.getTableState(fixtures.gameId)?.roundNumber)
    }

    /** 換位後下一局依新座位排列玩家，並在開局呈現前通知平台一次。 */
    @Test
    fun `a seat change reorders the players and tells the platform`() = runTest {
        val fixtures = Fixtures(
            transitionDirective = { RoundTransitionDirective.PassToNextSeat },
            seatOrder = { state -> state.players.reversed().map { it.id } },
        )
        val table = startedTable(fixtures.gameId, currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)
        assertIs<Outcome.Success<*>>(fixtures.exhaustiveDrawUseCase(fixtures.gameId))

        assertIs<Outcome.Success<*>>(fixtures.advanceRoundUseCase(fixtures.gameId))

        val reversed = table.players.reversed().map { it.id }
        val next = assertNotNull(fixtures.gameRepo.getTableState(fixtures.gameId))
        assertEquals(reversed, next.players.map { it.id })
        assertEquals(table.players[1].id, next.dealerPlayerId)
        assertEquals(listOf(reversed), fixtures.presentationPublisher.getPublishedSeatOrderChanges(fixtures.gameId))
    }

    /** 換位順序缺人時視為賽程錯誤，不開下一局，也不通知平台換位。 */
    @Test
    fun `an incomplete seat change is rejected`() = runTest {
        val fixtures = Fixtures(
            transitionDirective = { RoundTransitionDirective.PassToNextSeat },
            seatOrder = { state -> state.players.drop(1).map { it.id } },
        )
        val table = startedTable(fixtures.gameId, currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)
        assertIs<Outcome.Success<*>>(fixtures.exhaustiveDrawUseCase(fixtures.gameId))

        val result = fixtures.advanceRoundUseCase(fixtures.gameId)

        assertIs<GameError.InvalidMatchProgression>(assertIs<Outcome.Error<GameError>>(result).error)
        assertEquals(table.players.map { it.id }, fixtures.gameRepo.getTableState(fixtures.gameId)?.players?.map { it.id })
        assertEquals(emptyList(), fixtures.presentationPublisher.getPublishedSeatOrderChanges(fixtures.gameId))
    }

    /** 座位沒變時不通知平台換位。 */
    @Test
    fun `an unchanged seat order does not tell the platform`() = runTest {
        val fixtures = Fixtures(
            transitionDirective = { RoundTransitionDirective.PassToNextSeat },
            seatOrder = { state -> state.players.map { it.id } },
        )
        val table = startedTable(fixtures.gameId, currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)
        assertIs<Outcome.Success<*>>(fixtures.exhaustiveDrawUseCase(fixtures.gameId))

        assertIs<Outcome.Success<*>>(fixtures.advanceRoundUseCase(fixtures.gameId))

        assertEquals(emptyList(), fixtures.presentationPublisher.getPublishedSeatOrderChanges(fixtures.gameId))
    }

    /** 建立可供一般流局用例使用的固定局位桌況。 */
    private fun startedTable(gameId: Uuid, currentPlayerIndex: Int): TableState = FakeTableStateFactory.create(
        id = gameId,
        players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { seat ->
            FakeMahjongPlayerFactory.create(initialSeat = seat)
        },
        config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
        currentPlayerIndex = currentPlayerIndex,
    )
}
