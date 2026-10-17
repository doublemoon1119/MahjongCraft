package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

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
     * 建立指定莊家推進決策的完整流局用例測試環境。
     *
     * @param transitionDirective 本測試要求規則回傳的莊家去留。
     */
    private class Fixtures(
        transitionDirective: RoundTransitionDirective,
    ) {
        val gameId = Uuid.random()
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(RiichiRuleConfig::class, "test:round-transition") { config, id ->
                    TransitionOverrideRuleModule(
                        delegate = RiichiRuleModule(id, config),
                        transitionDirective = transitionDirective,
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
        private val presentationPublisher = FakeGamePresentationPublisher()
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

    /** 以委派保留日麻規則，只替測試指定本局結束後的莊家推進結果。 */
    private class TransitionOverrideRuleModule(
        private val delegate: RiichiRuleModule,
        private val transitionDirective: RoundTransitionDirective,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate {
        override fun resolveRoundTransition(
            context: RoundTransitionContext,
        ): RoundTransitionDirective = transitionDirective

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
        val fixtures = Fixtures(RoundTransitionDirective.REPEAT_DEALER)
        val table = startedTable(fixtures.gameId, currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.exhaustiveDrawUseCase(fixtures.gameId)

        assertIs<Outcome.Success<*>>(result)
        val completed = assertNotNull(fixtures.gameRepo.getGame(fixtures.gameId))
        assertEquals(RoundTransitionDirective.REPEAT_DEALER, completed.roundCompletion?.transitionDirective)
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
        val fixtures = Fixtures(RoundTransitionDirective.ADVANCE_DEALER)
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
        assertEquals(RoundTransitionDirective.ADVANCE_DEALER, completed.roundCompletion?.transitionDirective)
        assertEquals(RoundCompletionClassification.ABORTIVE_DRAW, completed.roundCompletion?.classification)

        val advanceResult = fixtures.advanceRoundUseCase(fixtures.gameId)

        assertIs<Outcome.Success<*>>(advanceResult)
        val next = assertNotNull(fixtures.gameRepo.getTableState(fixtures.gameId))
        assertEquals(table.roundNumber + 1, next.roundNumber)
        // 日麻的流局本場累加不因過莊改變，這裡只覆寫莊家去留。
        assertEquals(table.comboCount + 1, next.comboCount)
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
