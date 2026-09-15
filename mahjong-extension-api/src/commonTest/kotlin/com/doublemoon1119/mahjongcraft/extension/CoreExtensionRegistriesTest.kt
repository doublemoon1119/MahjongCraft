package com.doublemoon1119.mahjongcraft.extension

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
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
import kotlin.test.assertTrue

/** 驗證 [CoreExtensionRegistries] 的集中凍結入口。 */
class CoreExtensionRegistriesTest {
    /** 驗證 [CoreExtensionRegistries.freezeAll] 會凍結具有公開狀態的 core registry。 */
    @Test
    fun `freeze all freezes every core registry`() {
        val registries = CoreExtensionRegistries(
            moduleRegistry = MahjongModuleRegistryImpl(),
            tileTypeRegistry = TileTypeRegistryImpl(),
            networkRegistries = DefaultNetworkDtoRegistries(),
            persistenceRegistries = buildBuiltInPersistenceRegistries(),
            winCelebrationCueResolverRegistry = WinCelebrationCueResolverRegistryImpl(),
            gameActionAiRegistry = ExtensionGameActionAiRegistry(),
            gameActionCommandFactoryRegistry = ExtensionGameActionCommandFactoryRegistry(),
            gameCommandRegistry = ExtensionGameCommandExecutorRegistry(),
            postReactionRoundOutcomeResolverRegistry = PostReactionRoundOutcomeResolverRegistry(),
            postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry(),
            roundPreparationResolverRegistry = RoundPreparationResolverRegistry(),
            winRoundContinuationResolverRegistry = WinRoundContinuationResolverRegistry(),
            winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry(),
        )

        registries.freezeAll()

        assertTrue(registries.tileTypeRegistry.isFrozen)
        assertTrue(registries.winSettlementDetailResolverRegistry.isFrozen)
    }
}
