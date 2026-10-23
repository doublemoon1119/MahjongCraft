package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.runAsSingleWrite
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [SubmitRuleDecisionUseCase] 的單元測試。 */
class SubmitRuleDecisionUseCaseTest {
    /** 測試對局中唯一玩家的識別碼。 */
    private val playerId = Uuid.random()

    /**
     * 最後一位參與者確認後，步驟由規則解析並結束。
     *
     * 以目前的遊戲為預期遊戲執行，確認命令只寫入這一局一次（AI 命令的條件式提交契約）。
     */
    @Test
    fun `the last confirmation resolves the step`() = runTest {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val resolvers = RuleDecisionResolverRegistry().apply { register(ConfirmationResolver) }
        val presentationPublisher = FakeGamePresentationPublisher()
        val useCase = SubmitRuleDecisionUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = resolvers,
            snapshotSynchronizer = GameSnapshotSynchronizer(repository, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl(moduleRegistry)),
            presentationPublisher = presentationPublisher,
        )
        val table = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
            config = RiichiRuleConfig(),
        )
        repository.setGame(
            Game(
                tableState = table,
                flowConfig = GameFlowConfig(ruleDecision = RuleDecisionConfig(30)),
                pendingRuleDecision = PendingRuleDecision.open(
                    step = RuleDecisionStep(
                        stepId = "test:confirm",
                        stepIndex = 0,
                        inputSpecsByPlayerId = mapOf(playerId to RuleDecisionInputSpec.Confirmation),
                    ),
                    timing = RuleDecisionTiming.ROUND_START,
                ),
            ),
        )

        val windowId = repository.openWindowId(table.id)
        val result = repository.runAsSingleWrite(table.id) { useCase(table.id, playerId, windowId, RuleDecisionSubmission.Confirmed) }

        assertIs<Outcome.Success<Unit>>(result)
        assertNull(repository.getGame(table.id)?.pendingRuleDecision)
        assertEquals(table, repository.getGame(table.id)?.tableState)
    }

    /** 驗證兩位玩家完成換牌後才更新呈現，且只傳送交換後的牌張資料。 */
    @Test
    fun `completed tile exchange publishes updated hands only after all submissions`() = runTest {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val snapshotRepository = FakeGameSnapshotRepository()
        val presentationPublisher = FakeGamePresentationPublisher()
        val resolvers = RuleDecisionResolverRegistry().apply { register(TileExchangeResolver) }
        val firstTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        val secondTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9))
        val firstPlayerId = Uuid.random()
        val secondPlayerId = Uuid.random()
        val firstPlayer = FakeMahjongPlayerFactory.create(
            id = firstPlayerId,
            hand = Hand(tiles = listOf(firstTile)),
        )
        val secondPlayer = FakeMahjongPlayerFactory.create(
            id = secondPlayerId,
            hand = Hand(tiles = listOf(secondTile)),
        )
        val table = FakeTableStateFactory.create(
            players = listOf(firstPlayer, secondPlayer),
            config = RiichiRuleConfig(),
        )
        val game = Game(
            tableState = table,
            flowConfig = GameFlowConfig(ruleDecision = RuleDecisionConfig(30)),
            pendingRuleDecision = PendingRuleDecision.open(
                step = RuleDecisionStep(
                    stepId = "test:exchange",
                    stepIndex = 0,
                    inputSpecsByPlayerId = mapOf(
                        firstPlayerId to RuleDecisionInputSpec.TileSelection(setOf(firstTile.id), 1, 1),
                        secondPlayerId to RuleDecisionInputSpec.TileSelection(setOf(secondTile.id), 1, 1),
                    ),
                ),
                timing = RuleDecisionTiming.ROUND_START,
            ),
        )
        repository.setGame(game)
        val snapshotSynchronizer = GameSnapshotSynchronizer(
            repository,
            snapshotRepository,
            GameVisibilityPolicyImpl(moduleRegistry),
        )
        snapshotSynchronizer.sync(table.id, firstPlayerId)
        snapshotSynchronizer.sync(table.id, secondPlayerId)
        val useCase = SubmitRuleDecisionUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = resolvers,
            snapshotSynchronizer = snapshotSynchronizer,
            presentationPublisher = presentationPublisher,
        )

        val firstResult = useCase(table.id, firstPlayerId, repository.openWindowId(table.id), RuleDecisionSubmission.Tiles(setOf(firstTile.id)))

        assertIs<Outcome.Success<Unit>>(firstResult)
        assertTrue(presentationPublisher.getPublishedPlayerTilesUpdates(table.id).isEmpty())
        assertEquals(1, repository.historyDrafts.size)

        val secondResult = useCase(table.id, secondPlayerId, repository.openWindowId(table.id), RuleDecisionSubmission.Tiles(setOf(secondTile.id)))

        assertIs<Outcome.Success<Unit>>(secondResult)
        assertEquals(2, repository.historyDrafts.size)
        assertEquals(
            setOf(0, 1),
            presentationPublisher.getPublishedPlayerTilesUpdates(table.id).map { it.seatIndex }.toSet(),
        )
        assertEquals(
            secondTile.id,
            presentationPublisher.getPublishedPlayerTiles(table.id, 0)?.standingTileIds?.single(),
        )
        assertEquals(
            firstTile.id,
            presentationPublisher.getPublishedPlayerTiles(table.id, 1)?.standingTileIds?.single(),
        )
        assertNull(presentationPublisher.getPublishedInitialDeal(table.id))
        val firstObserverSnapshot = snapshotRepository.getSnapshot(table.id, firstPlayerId)!!
        val secondObserverSnapshot = snapshotRepository.getSnapshot(table.id, secondPlayerId)!!
        assertEquals(
            listOf(secondTile.id),
            firstObserverSnapshot.players.first { it.id == firstPlayerId }.hand.standingTiles.map { it.id },
        )
        assertTrue(
            firstObserverSnapshot.players.first { it.id == secondPlayerId }.hand.standingTiles.all { it.tile == null },
        )
        assertEquals(
            listOf(firstTile.id),
            secondObserverSnapshot.players.first { it.id == secondPlayerId }.hand.standingTiles.map { it.id },
        )
        assertTrue(
            secondObserverSnapshot.players.first { it.id == firstPlayerId }.hand.standingTiles.all { it.tile == null },
        )
        assertEquals(
            listOf(secondTile.id),
            repository.getGame(table.id)?.tableState?.players?.first { it.id == firstPlayerId }?.hand?.tiles?.map { it.id },
        )
        assertEquals(
            listOf(firstTile.id),
            repository.getGame(table.id)?.tableState?.players?.first { it.id == secondPlayerId }?.hand?.tiles?.map { it.id },
        )
    }

    /** 未收齊換牌提交時不應產生桌面牌張更新。 */
    @Test
    fun `incomplete tile exchange does not publish hand updates`() = runTest {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val presentationPublisher = FakeGamePresentationPublisher()
        val resolver = RuleDecisionResolverRegistry().apply { register(TileExchangeResolver) }
        val firstTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        val secondTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9))
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
                flowConfig = GameFlowConfig(ruleDecision = RuleDecisionConfig(30)),
                pendingRuleDecision = PendingRuleDecision.open(
                    step = RuleDecisionStep(
                        stepId = "test:exchange",
                        stepIndex = 0,
                        inputSpecsByPlayerId = mapOf(
                            firstPlayerId to RuleDecisionInputSpec.TileSelection(setOf(firstTile.id), 1, 1),
                            secondPlayerId to RuleDecisionInputSpec.TileSelection(setOf(secondTile.id), 1, 1),
                        ),
                    ),
                    timing = RuleDecisionTiming.ROUND_START,
                ),
            ),
        )
        val useCase = SubmitRuleDecisionUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = resolver,
            snapshotSynchronizer = GameSnapshotSynchronizer(
                repository,
                FakeGameSnapshotRepository(),
                GameVisibilityPolicyImpl(moduleRegistry),
            ),
            presentationPublisher = presentationPublisher,
        )

        val result = useCase(table.id, firstPlayerId, repository.openWindowId(table.id), RuleDecisionSubmission.Tiles(setOf(firstTile.id)))

        assertIs<Outcome.Success<Unit>>(result)
        assertTrue(presentationPublisher.getPublishedPlayerTilesUpdates(table.id).isEmpty())
        assertNull(presentationPublisher.getPublishedInitialDeal(table.id))
    }

    /** 無效的換牌提交應拒絕，且不改變桌況、歷史或呈現。 */
    @Test
    fun `invalid tile exchange submission has no side effects`() = runTest {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val snapshotRepository = FakeGameSnapshotRepository()
        val presentationPublisher = FakeGamePresentationPublisher()
        val firstPlayerId = Uuid.random()
        val firstTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        val table = FakeTableStateFactory.create(
            players = listOf(
                FakeMahjongPlayerFactory.create(id = firstPlayerId, hand = Hand(tiles = listOf(firstTile))),
            ),
            config = RiichiRuleConfig(),
        )
        val game = Game(
            tableState = table,
            flowConfig = GameFlowConfig(ruleDecision = RuleDecisionConfig(30)),
            pendingRuleDecision = PendingRuleDecision.open(
                step = RuleDecisionStep(
                    stepId = "test:exchange",
                    stepIndex = 0,
                    inputSpecsByPlayerId = mapOf(
                        firstPlayerId to RuleDecisionInputSpec.TileSelection(setOf(firstTile.id), 1, 1),
                    ),
                ),
                timing = RuleDecisionTiming.ROUND_START,
            ),
        )
        repository.setGame(game)
        val useCase = SubmitRuleDecisionUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = RuleDecisionResolverRegistry(),
            snapshotSynchronizer = GameSnapshotSynchronizer(
                repository,
                snapshotRepository,
                GameVisibilityPolicyImpl(moduleRegistry),
            ),
            presentationPublisher = presentationPublisher,
        )
        val invalidTileId = Uuid.random()

        val result = useCase(
            table.id,
            firstPlayerId,
            repository.openWindowId(table.id),
            RuleDecisionSubmission.Tiles(setOf(invalidTileId)),
        )

        assertEquals(
            Outcome.Error(GameError.InvalidRuleDecisionSubmission(table.id, firstPlayerId)),
            result,
        )
        assertEquals(game, repository.getGame(table.id))
        assertTrue(repository.historyDrafts.isEmpty())
        assertTrue(presentationPublisher.getPublishedPlayerTilesUpdates(table.id).isEmpty())
        assertNull(presentationPublisher.getPublishedInitialDeal(table.id))
    }

    @Test
    fun `a submission without timing configuration aborts before writing`() = runTest {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val table = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
            config = RiichiRuleConfig(),
        )
        val game = Game(
            tableState = table,
            flowConfig = GameFlowConfig(),
            pendingRuleDecision = PendingRuleDecision.open(
                step = RuleDecisionStep(
                    stepId = "test:confirm",
                    stepIndex = 0,
                    inputSpecsByPlayerId = mapOf(playerId to RuleDecisionInputSpec.Confirmation),
                ),
                timing = RuleDecisionTiming.ROUND_START,
            ),
        )
        repository.setGame(game)
        val resolverRegistry = RuleDecisionResolverRegistry().apply { register(PlayerNextResolver) }
        val useCase = SubmitRuleDecisionUseCase(
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

        assertFailsWith<RuleContractViolationException> {
            useCase(table.id, playerId, repository.openWindowId(table.id), RuleDecisionSubmission.Confirmed)
        }
        assertEquals(game, repository.getGame(table.id))
    }

    @Test
    fun `pending player rule decision with disabled policy aborts despite stale timing`() = runTest {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val table = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
            config = RiichiRuleConfig(redDoraCount = 0),
        )
        val game = Game(
            tableState = table,
            flowConfig = GameFlowConfig(ruleDecision = RuleDecisionConfig(30)),
            pendingRuleDecision = PendingRuleDecision.open(
                step = RuleDecisionStep(
                    stepId = "test:confirm",
                    stepIndex = 0,
                    inputSpecsByPlayerId = mapOf(playerId to RuleDecisionInputSpec.Confirmation),
                ),
                timing = RuleDecisionTiming.ROUND_START,
            ),
        )
        repository.setGame(game)
        val resolverRegistry = RuleDecisionResolverRegistry().apply { register(PlayerNextResolver) }
        val useCase = SubmitRuleDecisionUseCase(
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

        assertFailsWith<RuleContractViolationException> {
            useCase(table.id, playerId, repository.openWindowId(table.id), RuleDecisionSubmission.Confirmed)
        }
        assertEquals(game, repository.getGame(table.id))
    }

    /** 驗證提交指名的視窗不是目前開著的那一個時，即使步驟相同也視為過期而拒絕，不改變對局。 */
    @Test
    fun `submission for an earlier window with the same step is rejected`() = runTest {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val table = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
            config = RiichiRuleConfig(),
        )
        val step = RuleDecisionStep(
            stepId = "test:confirm",
            stepIndex = 0,
            inputSpecsByPlayerId = mapOf(playerId to RuleDecisionInputSpec.Confirmation),
        )
        val earlierWindow = PendingRuleDecision.open(step = step, timing = RuleDecisionTiming.ROUND_END)
        val game = Game(
            tableState = table,
            flowConfig = GameFlowConfig(ruleDecision = RuleDecisionConfig(30)),
            pendingRuleDecision = PendingRuleDecision.open(step = step, timing = RuleDecisionTiming.ROUND_END),
        )
        repository.setGame(game)
        val useCase = SubmitRuleDecisionUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = RuleDecisionResolverRegistry().apply { register(ConfirmationResolver) },
            snapshotSynchronizer = GameSnapshotSynchronizer(
                repository,
                FakeGameSnapshotRepository(),
                GameVisibilityPolicyImpl(moduleRegistry),
            ),
            presentationPublisher = FakeGamePresentationPublisher(),
        )

        val result = useCase(table.id, playerId, earlierWindow.id, RuleDecisionSubmission.Confirmed)

        assertEquals(Outcome.Error(GameError.RuleDecisionUnavailable(table.id, playerId)), result)
        assertEquals(game, repository.getGame(table.id))
    }

    /** 本局結算後的選擇改變分數時，當場以契約錯誤中止，不改變對局。 */
    @Test
    fun `a round end decision that changes scores is rejected`() = runTest {
        val (repository, useCase, game) = scoreChangingFixture(RuleDecisionTiming.ROUND_END)

        assertFailsWith<RuleContractViolationException> {
            useCase(game.id, playerId, assertNotNull(game.pendingRuleDecision).id, RuleDecisionSubmission.Confirmed)
        }
        assertEquals(game, repository.getGame(game.id))
    }

    /** 結算前的選擇可以改變分數。 */
    @Test
    fun `a decision before the settlement may change scores`() = runTest {
        val (repository, useCase, game) = scoreChangingFixture(RuleDecisionTiming.BEFORE_EXHAUSTIVE_DRAW)

        assertIs<Outcome.Success<Unit>>(useCase(game.id, playerId, assertNotNull(game.pendingRuleDecision).id, RuleDecisionSubmission.Confirmed))

        val player = assertNotNull(repository.getGame(game.id)).tableState.players.single()
        assertEquals(game.tableState.players.single().score + SCORE_CHANGE, player.score)
    }

    /**
     * 建立在 [timing] 開著確認視窗、確認後會改變分數的對局。
     *
     * @param timing 視窗開啟的時間點。
     * @return 保存對局的 repository、受測用例與開窗後的對局。
     */
    private suspend fun scoreChangingFixture(timing: RuleDecisionTiming): Triple<FakeGameRepository, SubmitRuleDecisionUseCase, Game> {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val table = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
            config = RiichiRuleConfig(),
        )
        val game = Game(
            tableState = table,
            flowConfig = GameFlowConfig(ruleDecision = RuleDecisionConfig(30)),
            pendingRuleDecision = PendingRuleDecision.open(
                step = RuleDecisionStep(
                    stepId = "test:confirm",
                    stepIndex = 0,
                    inputSpecsByPlayerId = mapOf(playerId to RuleDecisionInputSpec.Confirmation),
                ),
                timing = timing,
            ),
        )
        repository.setGame(game)
        val useCase = SubmitRuleDecisionUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = RuleDecisionResolverRegistry().apply { register(ScoreChangingResolver) },
            snapshotSynchronizer = GameSnapshotSynchronizer(repository, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl(moduleRegistry)),
            presentationPublisher = FakeGamePresentationPublisher(),
        )
        return Triple(repository, useCase, game)
    }

    /** 測試用解析器：確認完成後替每位玩家加上 [SCORE_CHANGE] 分。 */
    private object ScoreChangingResolver : RuleDecisionResolver {
        override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

        override fun ruleDecisionConfig(ruleConfig: MahjongRuleConfig): RuleDecisionConfig = RuleDecisionConfig(30)

        override fun begin(timing: RuleDecisionTiming, tableState: TableState, ruleModule: MahjongRuleModule<*>): RuleDecisionStep? = null

        override fun resolve(
            tableState: TableState,
            decision: PendingRuleDecision,
            ruleModule: MahjongRuleModule<*>,
        ): RuleDecisionResolution = RuleDecisionResolution(
            tableState.copy(players = tableState.players.map { it.copy(score = it.score + SCORE_CHANGE) }),
            nextStep = null,
        )
    }

    /** 測試常數。 */
    private companion object {
        /** [ScoreChangingResolver] 替每位玩家加上的分數。 */
        const val SCORE_CHANGE = 1000
    }

    /** 測試用解析器：不建立步驟，並在確認完成後結束選擇流程。 */
    private object ConfirmationResolver : RuleDecisionResolver {
        override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

        override fun ruleDecisionConfig(ruleConfig: MahjongRuleConfig): RuleDecisionConfig = RuleDecisionConfig(30)

        override fun begin(timing: RuleDecisionTiming, tableState: TableState, ruleModule: MahjongRuleModule<*>): RuleDecisionStep? = null

        override fun resolve(
            tableState: TableState,
            decision: PendingRuleDecision,
            ruleModule: MahjongRuleModule<*>,
        ): RuleDecisionResolution = RuleDecisionResolution(tableState, nextStep = null)
    }

    /** 測試用解析器：確認完成後要求下一個玩家步驟。 */
    private object PlayerNextResolver : RuleDecisionResolver {
        override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

        override fun ruleDecisionConfig(ruleConfig: MahjongRuleConfig): RuleDecisionConfig? = if ((ruleConfig as? RiichiRuleConfig)?.redDoraCount == 3) RuleDecisionConfig(30) else null

        override fun begin(timing: RuleDecisionTiming, tableState: TableState, ruleModule: MahjongRuleModule<*>): RuleDecisionStep? = null

        override fun resolve(
            tableState: TableState,
            decision: PendingRuleDecision,
            ruleModule: MahjongRuleModule<*>,
        ): RuleDecisionResolution = RuleDecisionResolution(
            tableState,
            RuleDecisionStep(
                stepId = "test:next",
                stepIndex = decision.stepIndex + 1,
                inputSpecsByPlayerId = mapOf(tableState.players.first().id to RuleDecisionInputSpec.Confirmation),
            ),
        )
    }

    /** 測試用換牌解析器，完成後交換兩位玩家各提交的一張牌。 */
    private object TileExchangeResolver : RuleDecisionResolver {
        override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

        override fun ruleDecisionConfig(ruleConfig: MahjongRuleConfig): RuleDecisionConfig = RuleDecisionConfig(30)

        override fun begin(timing: RuleDecisionTiming, tableState: TableState, ruleModule: MahjongRuleModule<*>): RuleDecisionStep? = null

        override fun resolve(
            tableState: TableState,
            decision: PendingRuleDecision,
            ruleModule: MahjongRuleModule<*>,
        ): RuleDecisionResolution {
            val players = tableState.players.map { player ->
                val submittedId = decision.submissionsByPlayerId.getValue(player.id)
                    .let { it as RuleDecisionSubmission.Tiles }
                    .tileIds
                    .single()
                player.copy(hand = player.hand.copy(tiles = player.hand.tiles.filterNot { it.id == submittedId }))
            }
            val submittedTiles = tableState.players
                .map { player -> decision.submissionsByPlayerId.getValue(player.id) }
                .map { (it as RuleDecisionSubmission.Tiles).tileIds.single() }
                .map { id -> tableState.players.flatMap { it.hand.standingTiles }.first { it.id == id } }
            val exchangedPlayers = players.mapIndexed { index, player ->
                player.copy(hand = player.hand.addTile(submittedTiles[(index + 1) % submittedTiles.size]))
            }
            return RuleDecisionResolution(tableState.copy(players = exchangedPlayers), nextStep = null)
        }
    }

    /**
     * 讀出目前開著的規則選擇視窗身分。
     *
     * @param gameId 對局識別碼。
     * @return 開著的視窗身分。
     */
    private suspend fun FakeGameRepository.openWindowId(gameId: Uuid): Uuid = assertNotNull(getGame(gameId)?.pendingRuleDecision).id
}
