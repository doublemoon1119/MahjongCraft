package com.doublemoon1119.mahjongcraft.testing.flow.bundled

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.bundled.BuiltInMahjongExtension
import com.doublemoon1119.mahjongcraft.bundled.BundledMahjongExtensions
import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.extension.MahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistryImpl

/** 測試用：在單一 registry 登記所有隨 MahjongCraft 發布之規則 extension 的規則模組。 */
fun MahjongModuleRegistry.registerBundledRuleModules() {
    BundledMahjongExtensions.all.forEach { it.registerRuleModules(this) }
}

/** 測試用：登記所有隨 MahjongCraft 發布之規則 extension 的網路 DTO。 */
fun NetworkDtoRegistries.registerBundledNetworkDtos() {
    BundledMahjongExtensions.all.forEach { it.registerNetworkDtos(this) }
}

/** 測試用：建立已登記所有隨 MahjongCraft 發布之規則 extension 存檔 mapper、尚未凍結的 persistence registries。 */
fun bundledPersistenceRegistries(): PersistenceRegistries = emptyPersistenceRegistries().apply {
    BundledMahjongExtensions.all.forEach { it.registerPersistenceDtos(this) }
}

/** 測試用：登記所有隨 MahjongCraft 發布之規則 extension 的回放轉換器。 */
fun HistoryReplayProjectionRegistry.registerBundledHistoryReplayProjections() {
    BundledMahjongExtensions.all.forEach { it.registerHistoryReplayProjections(this) }
}

/** 測試用：建立已登記所有隨 MahjongCraft 發布之規則 extension 胡牌演出提示、並已凍結的 registry。 */
fun bundledWinCelebrationCueResolverRegistry(): WinCelebrationCueResolverRegistry = WinCelebrationCueResolverRegistryImpl().apply {
    BundledMahjongExtensions.all.forEach { it.registerWinCelebrationCueResolvers(this) }
    freeze()
}

/**
 * 測試用：以 [BuiltInMahjongExtension] 登記內建 AI 策略。
 *
 * @param moduleRegistry 期望值策略依對局設定取得規則模組。
 * @param extensionActionRegistry 將規則擴充動作轉成命令候選。
 * @param opponentModelRegistry 期望值策略依規則取得對手模型。
 */
fun MahjongAiStrategyRegistry.registerBuiltInAiStrategies(
    moduleRegistry: MahjongModuleRegistry,
    extensionActionRegistry: ExtensionGameActionAiRegistry,
    opponentModelRegistry: OpponentModelRegistry,
) {
    BuiltInMahjongExtension(
        moduleRegistry = moduleRegistry,
        extensionActionRegistry = extensionActionRegistry,
        opponentModelRegistry = opponentModelRegistry,
    ).registerAiStrategies(this)
}

/** 測試用：以 [BuiltInMahjongExtension] 與所有隨 MahjongCraft 發布之規則 extension 完成登記並凍結的完整 registry。 */
fun bundledCoreExtensionRegistries(): CoreExtensionRegistries {
    val moduleRegistry = MahjongModuleRegistryImpl()
    val gameActionAiRegistry = ExtensionGameActionAiRegistry(moduleRegistry)
    val opponentModelRegistry = OpponentModelRegistry()
    val registries = CoreExtensionRegistries(
        moduleRegistry = moduleRegistry,
        tileTypeRegistry = TileTypeRegistryImpl(),
        networkRegistries = DefaultNetworkDtoRegistries(),
        persistenceRegistries = emptyPersistenceRegistries(),
        historyReplayProjectionRegistry = HistoryReplayProjectionRegistry(),
        winCelebrationCueResolverRegistry = WinCelebrationCueResolverRegistryImpl(),
        gameActionAiRegistry = gameActionAiRegistry,
        aiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = BuiltInAiStrategyKeys.BEGINNER),
        opponentModelRegistry = opponentModelRegistry,
        gameActionCommandFactoryRegistry = ExtensionGameActionCommandFactoryRegistry(),
        gameCommandRegistry = ExtensionGameCommandExecutorRegistry(),
        postReactionRoundOutcomeResolverRegistry = PostReactionRoundOutcomeResolverRegistry(),
        postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry(),
        roundPreparationResolverRegistry = RoundPreparationResolverRegistry(),
        winRoundContinuationResolverRegistry = WinRoundContinuationResolverRegistry(),
        winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry(),
    )
    MahjongExtensionRegistrar.registerAndFreeze(
        extensions = BundledMahjongExtensions.all,
        registries = registries,
        builtIn = BuiltInMahjongExtension(
            moduleRegistry = moduleRegistry,
            extensionActionRegistry = gameActionAiRegistry,
            opponentModelRegistry = opponentModelRegistry,
        ),
    )
    return registries
}

/** 測試用：以 [bundledCoreExtensionRegistries] 建立無頭歷史生成使用的 registry。 */
fun bundledHeadlessHistoryRegistries(): HeadlessHistoryRegistries = bundledCoreExtensionRegistries().let { registries ->
    HeadlessHistoryRegistries(
        moduleRegistry = registries.moduleRegistry,
        aiStrategyRegistry = registries.aiStrategyRegistry,
        winCelebrationCueResolverRegistry = registries.winCelebrationCueResolverRegistry,
        postActionExhaustiveDrawResolverRegistry = registries.postActionExhaustiveDrawResolverRegistry,
        postReactionRoundOutcomeResolverRegistry = registries.postReactionRoundOutcomeResolverRegistry,
        winRoundContinuationResolverRegistry = registries.winRoundContinuationResolverRegistry,
        winSettlementDetailResolverRegistry = registries.winSettlementDetailResolverRegistry,
        gameCommandRegistry = registries.gameCommandRegistry,
    )
}
