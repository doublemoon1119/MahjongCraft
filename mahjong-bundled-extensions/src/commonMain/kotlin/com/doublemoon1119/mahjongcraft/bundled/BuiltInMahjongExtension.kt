package com.doublemoon1119.mahjongcraft.bundled

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectedValueAiStrategy
import com.doublemoon1119.mahjongcraft.ai.expectation.InformationLevel
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.extension.MahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * MahjongCraft 不屬於任何規則的內建整合；所有規則中立的內建登記都寫在這裡。
 *
 * 平台把它交給 [MahjongExtensionRegistrar.registerAndFreeze] 的 `builtIn`，啟動報告將它的登記列為內建來源。
 * 登記方式與其他 extension 相同，不具有覆寫或繞過重複 key 驗證的特權。
 *
 * @property moduleRegistry 期望值策略依對局設定取得規則模組。
 * @property extensionActionRegistry 將規則擴充動作轉成命令候選。
 * @property opponentModelRegistry 期望值策略依規則取得對手模型。
 */
class BuiltInMahjongExtension(
    private val moduleRegistry: MahjongModuleRegistry,
    private val extensionActionRegistry: ExtensionGameActionAiRegistry,
    private val opponentModelRegistry: OpponentModelRegistry,
) : MahjongExtension {
    override val id: String = MahjongCraftMetadata.id("builtin")

    override fun registerRuleModules(registry: MahjongModuleRegistry) = Unit

    override fun registerNetworkDtos(registries: NetworkDtoRegistries) = Unit

    override fun registerPersistenceDtos(registries: PersistenceRegistries) = Unit

    /** 依初級、中級、高級、隨機出牌的順序登記；列出策略時依此順序呈現。 */
    override fun registerAiStrategies(registry: MahjongAiStrategyRegistry) {
        mapOf(
            BuiltInAiStrategyKeys.BEGINNER to InformationLevel.BEGINNER,
            BuiltInAiStrategyKeys.INTERMEDIATE to InformationLevel.INTERMEDIATE,
            BuiltInAiStrategyKeys.ADVANCED to InformationLevel.ADVANCED,
        ).forEach { (key, level) ->
            registry.register(key) {
                ExpectedValueAiStrategy(
                    level = level,
                    moduleRegistry = moduleRegistry,
                    extensionActionRegistry = extensionActionRegistry,
                    opponentModels = opponentModelRegistry,
                )
            }
        }
        registry.register(RandomAiStrategy.KEY) { RandomAiStrategy(extensionActionRegistry = extensionActionRegistry) }
    }
}
