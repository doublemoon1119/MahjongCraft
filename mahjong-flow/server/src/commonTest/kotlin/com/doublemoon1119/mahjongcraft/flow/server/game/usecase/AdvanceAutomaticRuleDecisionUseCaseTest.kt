package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [AdvanceAutomaticRuleDecisionUseCase] 的呈現更新測試。 */
class AdvanceAutomaticRuleDecisionUseCaseTest {
    /** 驗證多個連續自動步驟只在最後狀態產生一次受影響玩家更新。 */
    @Test
    fun `automatic rule decision publishes only the final hand changes`() = runTest {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val snapshotRepository = FakeGameSnapshotRepository()
        val presentationPublisher = FakeGamePresentationPublisher()
        val resolverRegistry = RuleDecisionResolverRegistry()
        val firstTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        val secondTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9))
        val replacementTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))
        val firstPlayerId = Uuid.random()
        val secondPlayerId = Uuid.random()
        val table = FakeTableStateFactory.create(
            players = listOf(
                FakeMahjongPlayerFactory.create(id = firstPlayerId, hand = Hand(tiles = listOf(firstTile))),
                FakeMahjongPlayerFactory.create(id = secondPlayerId, hand = Hand(tiles = listOf(secondTile))),
            ),
            config = RiichiRuleConfig(),
        )
        repository.setGame(
            Game(
                tableState = table,
                flowConfig = GameFlowConfig(),
                pendingRuleDecision = PendingRuleDecision.open(
                    step = RuleDecisionStep(
                        stepId = "test:auto-0",
                        stepIndex = 0,
                        inputSpecsByPlayerId = emptyMap(),
                    ),
                    timing = RuleDecisionTiming.WIN_CONTINUES,
                ),
            ),
        )
        val useCase = AdvanceAutomaticRuleDecisionUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = resolverRegistry,
            snapshotSynchronizer = GameSnapshotSynchronizer(
                repository,
                snapshotRepository,
                GameVisibilityPolicyImpl(moduleRegistry),
            ),
            presentationPublisher = presentationPublisher,
        )
        resolverRegistry.register(MultiStepResolver(replacementTile))

        assertTrue(useCase(table.id))

        val updates = presentationPublisher.getPublishedPlayerTilesUpdates(table.id)
        assertEquals(setOf(0, 1), updates.map { it.seatIndex }.toSet())
        assertTrue(updates.groupingBy { it.seatIndex }.eachCount().values.all { it == 1 })
        assertEquals(listOf(secondTile.id), presentationPublisher.getPublishedPlayerTiles(table.id, 0)?.standingTileIds)
        assertEquals(listOf(replacementTile.id), presentationPublisher.getPublishedPlayerTiles(table.id, 1)?.standingTileIds)
        assertTrue(presentationPublisher.getPublishedInitialDeal(table.id) == null)
        assertEquals(
            listOf(RuleDecisionTiming.WIN_CONTINUES, RuleDecisionTiming.WIN_CONTINUES),
            repository.historyDrafts.map { (it.fact as HistoryFact.RuleDecisionAutomaticallyResolved).timing },
        )
    }

    /** 驗證沒有自動步驟時不會回報變更或發布牌張更新。 */
    @Test
    fun `no automatic rule decision step reports no change`() = runTest {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val presentationPublisher = FakeGamePresentationPublisher()
        val resolverRegistry = RuleDecisionResolverRegistry().apply { register(NoStepResolver) }
        val table = FakeTableStateFactory.create(config = RiichiRuleConfig())
        repository.setGame(
            Game(
                tableState = table,
                flowConfig = GameFlowConfig(),
                pendingRuleDecision = null,
            ),
        )
        val useCase = AdvanceAutomaticRuleDecisionUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = resolverRegistry,
            snapshotSynchronizer = GameSnapshotSynchronizer(
                repository,
                FakeGameSnapshotRepository(),
                GameVisibilityPolicyImpl(moduleRegistry),
            ),
            presentationPublisher = presentationPublisher,
        )

        assertFalse(useCase(table.id))
        assertTrue(presentationPublisher.getPublishedPlayerTilesUpdates(table.id).isEmpty())
    }

    @Test
    fun `automatic step leading to player input requires timing configuration`() = runTest {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val table = FakeTableStateFactory.create(config = RiichiRuleConfig())
        val game = Game(
            tableState = table,
            flowConfig = GameFlowConfig(),
            pendingRuleDecision = PendingRuleDecision.open(
                step = RuleDecisionStep(
                    stepId = "test:auto",
                    stepIndex = 0,
                    inputSpecsByPlayerId = emptyMap(),
                ),
                timing = RuleDecisionTiming.ROUND_START,
            ),
        )
        repository.setGame(game)
        val resolverRegistry = RuleDecisionResolverRegistry().apply { register(AutoThenPlayerResolver) }
        val useCase = AdvanceAutomaticRuleDecisionUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = resolverRegistry,
            snapshotSynchronizer = GameSnapshotSynchronizer(
                repository,
                FakeGameSnapshotRepository(),
                GameVisibilityPolicyImpl(moduleRegistry),
            ),
            presentationPublisher = FakeGamePresentationPublisher(),
        )

        assertFailsWith<RuleContractViolationException> { useCase(table.id) }
        assertEquals(game, repository.getGame(table.id))
    }

    /**
     * 測試用兩步自動規則選擇解析器。
     *
     * @property replacementTile 第一個步驟放入玩家手中的測試牌。
     */
    private class MultiStepResolver(private val replacementTile: IdentifiedTile) : RuleDecisionResolver {
        override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

        override fun begin(timing: RuleDecisionTiming, tableState: TableState, ruleModule: MahjongRuleModule<*>): RuleDecisionStep? = null

        override fun resolve(
            tableState: TableState,
            decision: PendingRuleDecision,
            ruleModule: MahjongRuleModule<*>,
        ): RuleDecisionResolution = if (decision.stepIndex == 0) {
            RuleDecisionResolution(
                tableState.copy(
                    players = tableState.players.mapIndexed { index, player ->
                        if (index == 0) player.copy(hand = Hand(tiles = listOf(replacementTile))) else player
                    },
                ),
                RuleDecisionStep(
                    stepId = "test:auto-1",
                    stepIndex = 1,
                    inputSpecsByPlayerId = emptyMap(),
                ),
            )
        } else {
            val first = tableState.players[0].hand.tiles.single()
            val second = tableState.players[1].hand.tiles.single()
            RuleDecisionResolution(
                tableState.copy(
                    players = tableState.players.mapIndexed { index, player ->
                        player.copy(hand = Hand(tiles = listOf(if (index == 0) second else first)))
                    },
                ),
                null,
            )
        }
    }

    /** 測試用無選擇步驟解析器。 */
    private object NoStepResolver : RuleDecisionResolver {
        override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

        override fun begin(
            timing: RuleDecisionTiming,
            tableState: TableState,
            ruleModule: MahjongRuleModule<*>,
        ): RuleDecisionStep? = null

        override fun resolve(
            tableState: TableState,
            decision: PendingRuleDecision,
            ruleModule: MahjongRuleModule<*>,
        ) = RuleDecisionResolution(tableState, null)
    }

    /** 測試用解析器：自動步驟完成後要求玩家輸入。 */
    private object AutoThenPlayerResolver : RuleDecisionResolver {
        override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

        override fun begin(timing: RuleDecisionTiming, tableState: TableState, ruleModule: MahjongRuleModule<*>) = null

        override fun resolve(
            tableState: TableState,
            decision: PendingRuleDecision,
            ruleModule: MahjongRuleModule<*>,
        ) = RuleDecisionResolution(
            tableState,
            RuleDecisionStep(
                stepId = "test:player",
                stepIndex = decision.stepIndex + 1,
                inputSpecsByPlayerId = mapOf(tableState.players.first().id to RuleDecisionInputSpec.Confirmation),
            ),
        )
    }
}
