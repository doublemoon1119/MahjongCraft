package com.doublemoon1119.mahjongcraft.extension

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證 [CoreExtensionRegistries] 的集中凍結入口與診斷快照。 */
class CoreExtensionRegistriesTest {
    /** 驗證 [CoreExtensionRegistries.freezeAll] 會凍結具有公開狀態的 core registry，AI 策略 registry 也不能再登記。 */
    @Test
    fun `freeze all freezes every core registry`() {
        val registries = registries()

        registries.freezeAll()

        assertTrue(registries.tileTypeRegistry.isFrozen)
        assertTrue(registries.winSettlementDetailResolverRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            registries.aiStrategyRegistry.register("late") { RandomAiStrategy(registries.gameActionAiRegistry) }
        }
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

    /** 所有子 registry 皆為空的測試集合。 */
    private fun registries(): CoreExtensionRegistries = CoreExtensionRegistries(
        moduleRegistry = MahjongModuleRegistryImpl(),
        tileTypeRegistry = TileTypeRegistryImpl(),
        networkRegistries = DefaultNetworkDtoRegistries(),
        persistenceRegistries = buildBuiltInPersistenceRegistries(),
        winCelebrationCueResolverRegistry = WinCelebrationCueResolverRegistryImpl(),
        gameActionAiRegistry = ExtensionGameActionAiRegistry(),
        aiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = RandomAiStrategy.KEY),
        gameActionCommandFactoryRegistry = ExtensionGameActionCommandFactoryRegistry(),
        gameCommandRegistry = ExtensionGameCommandExecutorRegistry(),
        postReactionRoundOutcomeResolverRegistry = PostReactionRoundOutcomeResolverRegistry(),
        postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry(),
        roundPreparationResolverRegistry = RoundPreparationResolverRegistry(),
        winRoundContinuationResolverRegistry = WinRoundContinuationResolverRegistry(),
        winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry(),
    )
}
