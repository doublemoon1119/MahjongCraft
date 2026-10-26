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
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement.LegacyRiichiGameAchievementResolver
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

/** 驗證真實規則結果與終局提交經事件投影後，仍得到與舊判定相同的成果。 */
class RuleOutcomeAchievementIntegrationTest {
    /** 真實流局滿貫用例產生的規則結果，應與舊牌譜判定完全一致。 */
    @Test
    fun `real nagashi mangan outcome retains achievement results after event projection`() = runTest {
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
        assertAchievementEquivalence(modules, facts)
    }

    /** 真正的局末推進因負分結束對局，事件判定與舊判定都保留東一局及大差距擊飛。 */
    @Test
    fun `completed match submission retains achievement results after event projection`() = runTest {
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
        assertAchievementEquivalence(modules, facts)
    }

    /** 將真實提交投影為公開批次，比較搬移前後完整成果。 */
    private fun assertAchievementEquivalence(modules: MahjongModuleRegistryImpl, facts: CommittedGameFacts) {
        val resolverRegistry = GameAchievementResolverRegistryImpl().apply {
            BundledRiichiMinecraftExtension.registerGameAchievementResolvers(this)
        }
        val markerResolvers = GameEventPublicMarkerResolverRegistry().apply {
            register(GameEventPublicMarkerResolverDefinition(BuiltInRuleModuleIds.RIICHI, RiichiGameEventPublicMarkerResolver))
        }
        val batch = assertNotNull(GameEventProjector(modules, markerResolvers = markerResolvers).projectBatch(facts))
        val module = modules.getModule(RiichiRuleConfig())
        val expected = merge(
            LegacyGenericAchievementDetector.detect(facts, module),
            LegacyRiichiGameAchievementResolver().resolve(facts),
        )
        val actual = GameAchievementDetector(resolverRegistry).detect(batch).associate { it.playerId to it.achievementIds }
        assertEquals(expected, actual)
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
        val players = listOf(achiever) + listOf(Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(initialSeat = it) }
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

    /** 合併舊通用及日麻成果，保留同一玩家的一份集合。 */
    private fun merge(
        generic: Map<Uuid, Set<String>>,
        rule: Map<Uuid, Set<String>>,
    ): Map<Uuid, Set<String>> = (generic.keys + rule.keys).associateWith { playerId ->
        generic[playerId].orEmpty() + rule[playerId].orEmpty()
    }
}
