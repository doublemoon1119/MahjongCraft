package com.doublemoon1119.mahjongcraft.example.decision

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleModule
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.BeginRuleDecisionUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SubmitRuleDecisionUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 以實際規則選擇用例驗證簡易配對規則的換牌流程。 */
class ExamplePairingRuleDecisionUseCaseTest {
    /** 驗證三位玩家收齊兩張選牌後才原子交換，且未提交的選牌不會被其他觀察者看見。 */
    @Test
    fun `three players exchange two tiles only after every submission`() = runTest {
        val playerIds = List(3) { Uuid.random() }
        val config = ExamplePairingRuleConfig(exchangeEnabled = true)
        val module = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, config)
        val initialTable = GameInitializer.initialize(Uuid.random(), playerIds, module).tableState
        val store = AuthoritativeStateStore(historyRecordingEnabled = true, maxPendingHistoryEvents = 100)
        val repository = GameRepositoryImpl(store)
        val game = Game(
            tableState = initialTable,
            flowConfig = GameFlowConfig(ruleDecision = RuleDecisionConfig(30)),
        )
        store.update { state ->
            AuthoritativeStateUpdate(state.copy(games = state.games + (initialTable.id to game)), Unit)
        }
        val moduleRegistry = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(
                    configClass = ExamplePairingRuleConfig::class,
                    id = ExamplePairingIds.RULE_MODULE,
                    factory = { value, id -> ExamplePairingRuleModule(id, value) },
                ),
            )
            freeze()
        }
        val resolverRegistry = RuleDecisionResolverRegistry().apply {
            register(ExamplePairingRuleDecisionResolver())
            freeze()
        }
        val snapshotRepository = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(
            repository,
            snapshotRepository,
            GameVisibilityPolicyImpl(moduleRegistry),
        )
        val visibilityPolicy = GameVisibilityPolicyImpl(moduleRegistry)
        val begin = BeginRuleDecisionUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = resolverRegistry,
            snapshotSynchronizer = snapshotSynchronizer,
            abortGameHandler = GameAbortHandler { _, _, _ -> true },
        )
        val submit = SubmitRuleDecisionUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = resolverRegistry,
            snapshotSynchronizer = snapshotSynchronizer,
            presentationPublisher = FakeGamePresentationPublisher(),
        )
        val gameId = initialTable.id
        val orderedPlayerIds = initialTable.players.map { it.id }

        assertIs<Outcome.Success<Unit>>(begin(gameId))
        val ruleDecision = repository.getGame(gameId)!!.pendingRuleDecision!!
        orderedPlayerIds.forEach { snapshotSynchronizer.sync(gameId, it) }
        val selected = initialTable.players.associate { player ->
            player.id to player.hand.standingTiles.take(2).map { it.id }.toSet()
        }

        assertIs<Outcome.Error<*>>(
            submit(
                gameId,
                orderedPlayerIds[0],
                RuleDecisionSubmission.Tiles(selected.getValue(orderedPlayerIds[0]).take(1).toSet()),
            ),
        )
        assertIs<Outcome.Error<*>>(
            submit(
                gameId,
                orderedPlayerIds[0],
                RuleDecisionSubmission.Tiles(
                    selected.getValue(orderedPlayerIds[0]) + initialTable.players[0].hand.standingTiles[2].id,
                ),
            ),
        )
        assertIs<Outcome.Error<*>>(
            submit(
                gameId,
                orderedPlayerIds[0],
                RuleDecisionSubmission.Tiles(setOf(Uuid.random(), Uuid.random())),
            ),
        )

        assertIs<Outcome.Success<Unit>>(submit(gameId, orderedPlayerIds[0], RuleDecisionSubmission.Tiles(selected.getValue(orderedPlayerIds[0]))))
        val afterFirst = repository.getGame(gameId)!!
        assertEquals(initialTable.players.map { it.hand }, afterFirst.tableState.players.map { it.hand })
        snapshotSynchronizer.sync(gameId, orderedPlayerIds[0])
        snapshotSynchronizer.sync(gameId, orderedPlayerIds[1])
        assertEquals(
            RuleDecisionSubmission.Tiles(selected.getValue(orderedPlayerIds[0])),
            visibilityPolicy.ruleDecisionSnapshotFor(repository.getGame(gameId)!!, orderedPlayerIds[0])!!.ownSubmission,
        )
        assertNull(visibilityPolicy.ruleDecisionSnapshotFor(repository.getGame(gameId)!!, orderedPlayerIds[1])!!.ownSubmission)

        assertIs<Outcome.Success<Unit>>(submit(gameId, orderedPlayerIds[1], RuleDecisionSubmission.Tiles(selected.getValue(orderedPlayerIds[1]))))
        assertEquals(initialTable.players.map { it.hand }, repository.getGame(gameId)!!.tableState.players.map { it.hand })

        assertIs<Outcome.Success<Unit>>(submit(gameId, orderedPlayerIds[2], RuleDecisionSubmission.Tiles(selected.getValue(orderedPlayerIds[2]))))
        val resolved = repository.getGame(gameId)!!.tableState
        assertNull(repository.getGame(gameId)!!.pendingRuleDecision)
        orderedPlayerIds.forEachIndexed { index, playerId ->
            val sourceId = orderedPlayerIds[(index - 1 + orderedPlayerIds.size) % orderedPlayerIds.size]
            val expectedIncoming = selected.getValue(sourceId)
            val actualIncoming = resolved.players.first { it.id == playerId }.hand.standingTiles
                .map { it.id }.toSet() intersect expectedIncoming
            assertEquals(expectedIncoming, actualIncoming)
        }
        assertEquals(
            initialTable.players.flatMap { it.hand.standingTiles }.map { it.id }.toSet(),
            resolved.players.flatMap { it.hand.standingTiles }.map { it.id }.toSet(),
        )
        assertTrue(resolved.players.flatMap { it.hand.standingTiles }.map { it.id }.distinct().size == 9)
        assertEquals(ruleDecision.stepId, ExamplePairingIds.RULE_MODULE + "/exchange")
    }
}
