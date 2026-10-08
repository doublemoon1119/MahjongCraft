package com.doublemoon1119.mahjongcraft.extension

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.expectation.NeutralOpponentModel
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.bundled.BundledRiichiExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistryImpl
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證 [CoreExtensionRegistries] 的集中凍結入口與診斷快照。 */
class CoreExtensionRegistriesTest {
    /** 驗證 [CoreExtensionRegistries.freezeAll] 會凍結具有公開狀態的 core registry，AI 策略與對手模型 registry 也不能再登記。 */
    @Test
    fun `freeze all freezes every core registry`() {
        val registries = registries()

        registries.freezeAll()

        assertTrue(registries.tileTypeRegistry.isFrozen)
        assertTrue(registries.winSettlementDetailResolverRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            registries.aiStrategyRegistry.register("late") { RandomAiStrategy(registries.gameActionAiRegistry) }
        }
        assertFailsWith<IllegalStateException> {
            registries.opponentModelRegistry.register("example:late") { _, depth -> NeutralOpponentModel(depth) }
        }
        assertFailsWith<IllegalStateException> {
            registries.historyReplayProjectionRegistry.registerAction("example:late") { _, _ ->
                HistoryReplayFact.Opaque("example:late")
            }
        }
    }

    /** 驗證診斷快照包含對手模型，新登記的規則會出現在前後快照的差異中。 */
    @Test
    fun `registration snapshot tracks opponent models`() {
        val registries = registries()
        val before = registries.registrationSnapshot()
        BundledRiichiExtension.registerOpponentModels(registries.opponentModelRegistry)

        assertEquals(
            listOf(
                ExtensionRegistrationCategory(
                    id = "mahjongcraft:opponent_model",
                    displayName = "Opponent Model",
                    registrationIds = listOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.RIICHI_THREE_PLAYER),
                ),
            ),
            before.additionsSince(registries.registrationSnapshot()),
        )
    }

    /** 驗證診斷快照包含 AI 策略，新登記的策略會出現在前後快照的差異中。 */
    @Test
    fun `registration snapshot tracks ai strategies`() {
        val registries = registries()
        val before = registries.registrationSnapshot()
        registries.aiStrategyRegistry.register(RandomAiStrategy.KEY) { RandomAiStrategy(registries.gameActionAiRegistry) }

        assertEquals(
            listOf(ExtensionRegistrationCategory("mahjongcraft:ai_strategy", "AI Strategy", listOf(RandomAiStrategy.KEY))),
            before.additionsSince(registries.registrationSnapshot()),
        )
    }

    /** 驗證歷史投影 registry 的分類 key 會出現在診斷快照差異中。 */
    @Test
    fun `registration snapshot tracks history replay projections`() {
        val registries = registries()
        val before = registries.registrationSnapshot()
        registries.historyReplayProjectionRegistry.registerAction("example:action") { _, _ ->
            HistoryReplayFact.Opaque("example:action")
        }

        assertEquals(
            listOf(
                ExtensionRegistrationCategory(
                    "mahjongcraft:history_replay_action",
                    "History Replay Action",
                    listOf("example:action"),
                ),
            ),
            before.additionsSince(registries.registrationSnapshot()),
        )
    }

    /** 所有子 registry 皆為空的測試集合。 */
    private fun registries(): CoreExtensionRegistries = CoreExtensionRegistries(
        moduleRegistry = MahjongModuleRegistryImpl(),
        tileTypeRegistry = TileTypeRegistryImpl(),
        networkRegistries = DefaultNetworkDtoRegistries(),
        persistenceRegistries = bundledPersistenceRegistries(),
        historyReplayProjectionRegistry = HistoryReplayProjectionRegistry(),
        winCelebrationCueResolverRegistry = WinCelebrationCueResolverRegistryImpl(),
        gameActionAiRegistry = ExtensionGameActionAiRegistry(MahjongModuleRegistryImpl()),
        aiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = RandomAiStrategy.KEY),
        opponentModelRegistry = OpponentModelRegistry(),
        gameActionCommandFactoryRegistry = ExtensionGameActionCommandFactoryRegistry(),
        gameCommandRegistry = ExtensionGameCommandExecutorRegistry(),
        postReactionRoundOutcomeResolverRegistry = PostReactionRoundOutcomeResolverRegistry(),
        postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry(),
        roundPreparationResolverRegistry = RoundPreparationResolverRegistry(),
        winRoundContinuationResolverRegistry = WinRoundContinuationResolverRegistry(),
        winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry(),
    )
}
