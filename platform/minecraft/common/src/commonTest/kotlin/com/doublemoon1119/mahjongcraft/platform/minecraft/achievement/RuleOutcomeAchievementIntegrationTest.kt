package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.bundled.BundledRiichiExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverDefinition
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiGameEventPublicMarkerResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ResolvePostReactionRoundOutcomeUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.CommittedFactsListener
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement.RiichiAchievementIds
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證真實流局滿貫與擊飛終局經公開事件產生完整成果與統計。 */
class RuleOutcomeAchievementIntegrationTest {
    /** 只有未捨中張的東家取得流局滿貫與一般和牌成果，不計為自摸或榮和。 */
    @Test
    fun `real nagashi mangan awards only its achiever and increments wins once`() = runTest {
        val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val repository = GameRepositoryImpl(store)
        val snapshotSynchronizer = GameSnapshotSynchronizer(
            repository,
            FakeGameSnapshotRepository(),
            GameVisibilityPolicyImpl(modules),
        )
        val outcomeResolvers = PostReactionRoundOutcomeResolverRegistry().apply {
            BundledRiichiExtension.registerPostReactionRoundOutcomeResolvers(this)
            freeze()
        }
        val detailResolvers = WinSettlementDetailResolverRegistry().apply {
            BundledRiichiExtension.registerWinSettlementDetailResolvers(this)
            freeze()
        }
        val useCase = ResolvePostReactionRoundOutcomeUseCase(
            gameRepository = repository,
            moduleRegistry = modules,
            resolverRegistry = outcomeResolvers,
            snapshotSynchronizer = snapshotSynchronizer,
            winSettlementDetailResolverRegistry = detailResolvers,
        )
        val game = nagashiGame()
        val submitted = captureCommittedFacts(store)
        repository.updateGame(game.id, history = { _, _, _ -> emptyList() }) { game to Unit }

        val result = useCase(game.id)
        assertTrue(result is Outcome.Success && result.value?.id == RiichiRoundOutcomeIds.NAGASHI_MANGAN)
        val facts = assertNotNull(submitted.lastOrNull())
        val winnerId = game.tableState.players.first().id
        assertAchievements(
            modules = modules,
            facts = facts,
            expectedAchievements = mapOf(winnerId to setOf(BuiltInAchievementIds.WIN, RiichiAchievementIds.NAGASHI_MANGAN)),
            expectedStatistics = mapOf(winnerId to setOf(AchievementStatisticIds.WINS)),
        )
    }

    /** 真正的局末推進因負分結束對局，授予完成、排名及東一局的大差距擊飛成果。 */
    @Test
    fun `busted match completion awards exact completion ranking and bust outcomes`() = runTest {
        val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val initial = nagashiGame()
        val state = initial.tableState.copy(
            players = initial.tableState.players.mapIndexed { index, player -> player.copy(score = if (index == 0) -30_000 else 45_000) },
        )
        val game = initial.copy(
            tableState = state,
            roundCompletion = RoundCompletionSummary(
                outcomeId = BuiltInRoundOutcomeIds.TSUMO,
                classification = RoundCompletionClassification.WIN,
                beneficiaryPlayerIds = setOf(state.players[1].id),
                transitionDirective = RoundTransitionDirective.PassToNextSeat,
                settledScoresByPlayerId = state.players.associate { it.id to it.score },
            ),
        )
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val repository = GameRepositoryImpl(store)
        val submitted = captureCommittedFacts(store)
        repository.updateGame(game.id, history = { _, _, _ -> emptyList() }) { game to Unit }
        val useCase = AdvanceRoundUseCase(
            gameRepository = repository,
            moduleRegistry = modules,
            snapshotSynchronizer = GameSnapshotSynchronizer(repository, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl(modules)),
            handSortPreferenceStore = HandSortPreferenceStore(),
            eventPublisher = FakeGameEventPublisher(),
            presentationPublisher = FakeGamePresentationPublisher(),
        )
        assertTrue(useCase(game.id) is Outcome.Success, "The real progression should end a busted match")

        val facts = assertNotNull(submitted.lastOrNull())
        assertTrue(facts.facts.any { (it.fact as? HistoryFact.MatchCompleted)?.reasonId == BuiltInMatchEndReasonIds.PLAYER_BUSTED })
        val ids = state.players.map { it.id }
        val completed = setOf(BuiltInAchievementIds.MATCH_COMPLETED, RiichiAchievementIds.MATCH_COMPLETED)
        assertAchievements(
            modules = modules,
            facts = facts,
            expectedAchievements = mapOf(
                ids[0] to completed + setOf(
                    BuiltInAchievementIds.LAST_PLACE,
                    RiichiAchievementIds.BUSTED,
                    RiichiAchievementIds.BUSTED_FAR,
                    RiichiAchievementIds.BUSTED_IN_EAST_ONE,
                ),
                ids[1] to completed + BuiltInAchievementIds.FIRST_PLACE,
                ids[2] to completed,
                ids[3] to completed,
            ),
            expectedStatistics = mapOf(
                ids[0] to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
                ids[1] to setOf(AchievementStatisticIds.MATCHES_COMPLETED, AchievementStatisticIds.FIRST_PLACES),
                ids[2] to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
                ids[3] to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
            ),
        )
    }

    /** 將真實提交投影為公開批次，驗證每位玩家的完整成果、順序與統計。 */
    private fun assertAchievements(
        modules: MahjongModuleRegistryImpl,
        facts: CommittedGameFacts,
        expectedAchievements: Map<Uuid, Set<String>>,
        expectedStatistics: Map<Uuid, Set<String>>,
    ) {
        val resolverRegistry = GameAchievementResolverRegistryImpl().apply {
            BundledRiichiMinecraftExtension.registerGameAchievementResolvers(this)
        }
        val markerResolvers = GameEventPublicMarkerResolverRegistry().apply {
            register(GameEventPublicMarkerResolverDefinition(BuiltInRuleModuleIds.RIICHI, RiichiGameEventPublicMarkerResolver))
        }
        val batch = assertNotNull(GameEventProjector(modules, markerResolvers = markerResolvers).projectBatch(facts))
        val awards = GameAchievementDetector(resolverRegistry).detect(batch)
        assertEquals(expectedAchievements.keys.toList(), awards.map { it.playerId })
        assertEquals(expectedAchievements, awards.associate { it.playerId to it.achievementIds })
        assertEquals(expectedStatistics, awards.associate { it.playerId to AchievementStatisticIds.statisticsFor(it.achievementIds) })
    }

    /** 擷取 store 真正提交的事實，略過未產生事實的準備寫入。 */
    private fun captureCommittedFacts(store: AuthoritativeStateStore): MutableList<CommittedGameFacts> {
        val submitted = mutableListOf<CommittedGameFacts>()
        store.setCommittedFactsListener(object : CommittedFactsListener {
            override fun onCommitted(sequence: Long, facts: CommittedGameFacts) {
                submitted += facts
            }

            override fun onReleased(sequence: Long) = Unit
        })
        return submitted
    }

    /** 建立東家只捨幺九牌的日麻對局。 */
    private fun nagashiGame(): Game {
        val achiever = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            discardPile = allYaochuuDiscardPile(),
        )
        val players = listOf(achiever) + listOf(Wind.SOUTH, Wind.WEST, Wind.NORTH).map {
            FakeMahjongPlayerFactory.create(
                initialSeat = it,
                discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 2))),
            )
        }
        return Game(
            tableState = FakeTableStateFactory.create(players = players, config = RiichiRuleConfig()),
            flowConfig = GameFlowConfig(),
        )
    }

    /** 建立沒有被鳴走的幺九牌牌河。 */
    private fun allYaochuuDiscardPile(): RiichiDiscardPile = RiichiDiscardPile()
        .discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East))
        .discardTile(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)))
        .discardTile(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)))
}
