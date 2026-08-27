package com.doublemoon1119.mahjongcraft.flow.server.di

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.registerBuiltInAiStrategies
import com.doublemoon1119.mahjongcraft.flow.common.di.FlowCommonModule
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.createBuiltInWinSettlementDetailResolverRegistry
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * `:mahjong-flow-server` 的 Koin 模組。
 *
 * 絕大多數綁定靠 [ComponentScan] + 類別自身的 `@Factory` 標註自動完成。[mahjongAiStrategyRegistry]
 * 是目前唯一一處手動綁定——`:mahjong-ai` 刻意不依賴 Koin（比照 `:mahjong-logic` 維持框架無關），
 * [MahjongAiStrategyRegistryImpl] 因此不會被這裡的 [ComponentScan] 掃到（套件字首不同），需要顯式
 * 提供。
 */
@Module(includes = [FlowCommonModule::class])
@ComponentScan("com.doublemoon1119.mahjongcraft.flow.server")
class FlowServerModule {
    /** 建立供 bundled 與第三方規則 extension 在 bootstrap 階段登記 handler 的 registry。 */
    @Single
    fun extensionGameCommandExecutorRegistry(): ExtensionGameCommandExecutorRegistry = ExtensionGameCommandExecutorRegistry()

    /** 建立已包含內建規則、並開放 extension 啟動期登記的 AI action registry。 */
    @Single
    fun extensionGameActionAiRegistry(): ExtensionGameActionAiRegistry = ExtensionGameActionAiRegistry()

    /** 建立供規則 extension 登記最終捨牌後特殊結果的 registry。 */
    @Single
    fun postReactionRoundOutcomeResolverRegistry(): PostReactionRoundOutcomeResolverRegistry = PostReactionRoundOutcomeResolverRegistry()

    /** 建立供規則 extension 登記胡牌即時結算後續決策的 registry。 */
    @Single
    fun winRoundContinuationResolverRegistry(): WinRoundContinuationResolverRegistry = WinRoundContinuationResolverRegistry()

    /** 建立含 bundled 日麻解析器、並開放 extension 啟動期登記的胡牌詳情 registry。 */
    @Single
    fun winSettlementDetailResolverRegistry(): WinSettlementDetailResolverRegistry = createBuiltInWinSettlementDetailResolverRegistry()

    @Single
    fun mahjongAiStrategyRegistry(extensionActionRegistry: ExtensionGameActionAiRegistry): MahjongAiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = RandomAiStrategy.KEY).apply {
        registerBuiltInAiStrategies(extensionActionRegistry)
    }
}
