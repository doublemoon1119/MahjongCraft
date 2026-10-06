package com.doublemoon1119.mahjongcraft.flow.server.di

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.flow.common.di.FlowCommonModule
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * `:mahjong-flow-server` 的 Koin 模組。
 *
 * 絕大多數綁定靠 [ComponentScan] + 類別自身的 `@Factory` 標註自動完成；各 registry 由這裡以 `@Single` 提供空的實例，
 * 內容由 extension bootstrap 登記。`:mahjong-ai` 刻意不依賴 Koin（比照 `:mahjong-logic` 維持框架無關），
 * [MahjongAiStrategyRegistryImpl] 因此不會被這裡的 [ComponentScan] 掃到（套件字首不同），需要顯式提供。
 */
@Module(includes = [FlowCommonModule::class])
@ComponentScan("com.doublemoon1119.mahjongcraft.flow.server")
class FlowServerModule {
    /** 建立供規則 extension 登記玩家動作命令 factory 的 registry。 */
    @Single
    fun extensionGameActionCommandFactoryRegistry(): ExtensionGameActionCommandFactoryRegistry = ExtensionGameActionCommandFactoryRegistry()

    /** 建立供規則 extension 登記開局準備流程的 registry。 */
    @Single
    fun roundPreparationResolverRegistry(): RoundPreparationResolverRegistry = RoundPreparationResolverRegistry()

    /** 建立供 bundled 與第三方規則 extension 在 bootstrap 階段登記 handler 的 registry。 */
    @Single
    fun extensionGameCommandExecutorRegistry(): ExtensionGameCommandExecutorRegistry = ExtensionGameCommandExecutorRegistry()

    /** 建立開放 extension 啟動期登記的 AI action registry。 */
    @Single
    fun extensionGameActionAiRegistry(moduleRegistry: MahjongModuleRegistry): ExtensionGameActionAiRegistry = ExtensionGameActionAiRegistry(moduleRegistry)

    /** 建立供規則 extension 登記最終捨牌後特殊結果的 registry。 */
    @Single
    fun postReactionRoundOutcomeResolverRegistry(): PostReactionRoundOutcomeResolverRegistry = PostReactionRoundOutcomeResolverRegistry()

    /** 建立供規則 extension 登記主動觸發途中流局判定的 registry。 */
    @Single
    fun postActionExhaustiveDrawResolverRegistry(): PostActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry()

    /** 建立供規則 extension 登記胡牌即時結算後續決策的 registry。 */
    @Single
    fun winRoundContinuationResolverRegistry(): WinRoundContinuationResolverRegistry = WinRoundContinuationResolverRegistry()

    /** 建立供規則 extension 登記胡牌詳情解析器的 registry。 */
    @Single
    fun winSettlementDetailResolverRegistry(): WinSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry()

    /**
     * 建立 AI 策略 registry；未知或未指定的策略 key 退回初級。內建策略由 extension bootstrap 與其他內建項目一起登記，
     * 這裡不預先登記任何策略。
     */
    @Single
    fun mahjongAiStrategyRegistry(): MahjongAiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = BuiltInAiStrategyKeys.BEGINNER)

    /** 建立供規則 extension 登記對手模型的 registry；沒有登記的規則使用不具規則知識的對手模型。 */
    @Single
    fun opponentModelRegistry(): OpponentModelRegistry = OpponentModelRegistry()
}
