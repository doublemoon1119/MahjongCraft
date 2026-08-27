package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementMode
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [ResolveWinRoundContinuationUseCase] 的 context 重建、預設值與 directive 套用測試。 */
class ResolveWinRoundContinuationUseCaseTest {
    private val gameId = Uuid.random()
    private val gameRepo = FakeGameRepository()
    private val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
    private val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl())
    private val winPresentationHandoff = WinPresentationHandoff()
    private val ruleModuleId = moduleRegistry.getModule(RiichiRuleConfig()).id

    /** 未替該規則模組登記任何 resolver 時，回傳 EndRound 且完全不修改已結算的桌況。 */
    @Test
    fun `invoke returns EndRound and leaves table state unchanged when no resolver is registered`() = runTest {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val winner = FakeMahjongPlayerFactory.create(hand = Hand(lastDrawn = winningTile))
        val previousState = FakeTableStateFactory.create(id = gameId, players = listOf(winner), config = RiichiRuleConfig())
        val settledState = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(winner.recordAction(GameAction.Tsumo)),
            config = RiichiRuleConfig(),
        )
        gameRepo.setTableState(settledState)
        val useCase = ResolveWinRoundContinuationUseCase(gameRepo, moduleRegistry, WinRoundContinuationResolverRegistry().apply { freeze() }, snapshotSynchronizer)

        val result = useCase(gameId, previousState, setOf(winner.id))

        assertTrue(result is Outcome.Success)
        assertEquals(WinRoundDirective.EndRound, result.value)
        assertEquals(settledState, gameRepo.getTableState(gameId))
    }

    /** 自摸時，胡牌張應從贏家仍持有的 `lastDrawn` 取得，且 `ronDiscarderId` 為 null。 */
    @Test
    fun `invoke builds tsumo context from winner's lastDrawn with no discarder`() = runTest {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val winner = FakeMahjongPlayerFactory.create(hand = Hand(lastDrawn = winningTile))
        val previousState = FakeTableStateFactory.create(id = gameId, players = listOf(winner), config = RiichiRuleConfig())
        val settledState = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(winner.recordAction(GameAction.Tsumo)),
            config = RiichiRuleConfig(),
        )
        gameRepo.setTableState(settledState)
        val capturedContext = captureContext(previousState, setOf(winner.id))

        assertEquals(winningTile.id, capturedContext.winningTileId)
        assertEquals(null, capturedContext.ronDiscarderId)
        assertEquals(setOf(winner.id), capturedContext.winnerPlayerIds)
    }

    /** 榮和時，放銃者應從 [TableState.pendingReaction]（結算前尚未清除）還原，胡牌張取自 `GameAction.Ron.tileId`。 */
    @Test
    fun `invoke builds ron context from previous pendingReaction discarder and Ron action tile`() = runTest {
        val discarderId = Uuid.random()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val winner = FakeMahjongPlayerFactory.create()
        val previousState = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(winner),
            config = RiichiRuleConfig(),
            pendingReaction = PendingReaction(discarderId, winningTile.id, setOf(winner.id)),
        )
        val settledState = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(winner.recordAction(GameAction.Ron(winningTile.id))),
            config = RiichiRuleConfig(),
        )
        gameRepo.setTableState(settledState)
        val capturedContext = captureContext(previousState, setOf(winner.id))

        assertEquals(discarderId, capturedContext.ronDiscarderId)
        assertEquals(winningTile.id, capturedContext.winningTileId)
    }

    /** [WinRoundDirective.ContinueRound] 應被原子套用到權威 [TableState]。 */
    @Test
    fun `invoke applies ContinueRound directive to persisted table state`() = runTest {
        val winner = FakeMahjongPlayerFactory.create(hand = Hand(lastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)))
        val nextPlayer = FakeMahjongPlayerFactory.create()
        val previousState = FakeTableStateFactory.create(id = gameId, players = listOf(winner, nextPlayer), config = RiichiRuleConfig())
        val settledState = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(winner.recordAction(GameAction.Tsumo), nextPlayer),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        gameRepo.setTableState(settledState)
        val registry = WinRoundContinuationResolverRegistry().apply {
            register(
                fixedDirectiveResolver(
                    WinRoundDirective.ContinueRound(
                        newlyFinishedPlayerIds = setOf(winner.id),
                        nextPlayerId = nextPlayer.id,
                        settlementMode = ContinuingWinSettlementMode.FULL,
                    ),
                ),
            )
            freeze()
        }
        val useCase = ResolveWinRoundContinuationUseCase(gameRepo, moduleRegistry, registry, snapshotSynchronizer)

        val result = useCase(gameId, previousState, setOf(winner.id))

        assertTrue(result is Outcome.Success)
        assertTrue(result.value is WinRoundDirective.ContinueRound)
        val updated = gameRepo.getTableState(gameId)!!
        assertEquals(setOf(winner.id), updated.finishedPlayerIds)
        assertEquals(1, updated.currentPlayerIndex)
    }

    /** 呼叫 [useCase] 一次並回傳唯一登記的 resolver 觀察到的 context（該 resolver 固定回傳 null）。 */
    private suspend fun captureContext(previousState: com.doublemoon1119.mahjongcraft.logic.table.TableState, winnerPlayerIds: Set<Uuid>): WinRoundContinuationContext {
        var captured: WinRoundContinuationContext? = null
        val registry = WinRoundContinuationResolverRegistry().apply {
            register(
                object : WinRoundContinuationResolver {
                    override val id: String = "test:capture"
                    override val ruleModuleId: String = this@ResolveWinRoundContinuationUseCaseTest.ruleModuleId
                    override val priority: Int = 0

                    override fun resolve(context: WinRoundContinuationContext, ruleModule: MahjongRuleModule<*>): WinRoundDirective? {
                        captured = context
                        return null
                    }
                },
            )
            freeze()
        }
        val useCase = ResolveWinRoundContinuationUseCase(gameRepo, moduleRegistry, registry, snapshotSynchronizer)
        useCase(gameId, previousState, winnerPlayerIds)
        return checkNotNull(captured) { "resolver was not invoked" }
    }

    /** 建立固定回傳 [directive] 的測試 resolver。 */
    private fun fixedDirectiveResolver(directive: WinRoundDirective): WinRoundContinuationResolver = object : WinRoundContinuationResolver {
        override val id: String = "test:fixed"
        override val ruleModuleId: String = this@ResolveWinRoundContinuationUseCaseTest.ruleModuleId
        override val priority: Int = 0

        override fun resolve(context: WinRoundContinuationContext, ruleModule: MahjongRuleModule<*>): WinRoundDirective = directive
    }
}
