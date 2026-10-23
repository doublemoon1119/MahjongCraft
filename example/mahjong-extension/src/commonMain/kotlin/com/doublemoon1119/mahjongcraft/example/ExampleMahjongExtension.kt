package com.doublemoon1119.mahjongcraft.example

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyDefinition
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.example.action.registerExamplePairingDeclarationFactory
import com.doublemoon1119.mahjongcraft.example.action.registerExamplePairingDeclarationHandler
import com.doublemoon1119.mahjongcraft.example.ai.ExampleAiStrategyIds
import com.doublemoon1119.mahjongcraft.example.ai.ExampleTsumogiriAiStrategy
import com.doublemoon1119.mahjongcraft.example.decision.ExamplePairingRuleDecisionResolver
import com.doublemoon1119.mahjongcraft.example.format.registerExampleNetworkDtos
import com.doublemoon1119.mahjongcraft.example.format.registerExamplePersistenceDtos
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleModule
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverDefinition
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeDefinition
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry

/** 平台無關的範例擴充入口；平台轉接層可繼承並補上自己的呈現與原生整合。 */
open class ExampleMahjongExtension : MahjongExtension {
    override val id: String = ExampleExtensionIds.EXTENSION

    /** 透過公開工廠登記簡易配對規則，不依賴任何內建規則模組。 */
    override fun registerRuleModules(registry: MahjongModuleRegistry) {
        registry.register(
            definition = MahjongModuleDefinition(
                configClass = ExamplePairingRuleConfig::class,
                id = ExamplePairingIds.RULE_MODULE,
                factory = { config, id -> ExamplePairingRuleModule(id = id, config = config) },
            ),
        )
    }

    /** 登記倒置一萬的獨立牌種身分。 */
    override fun registerTileTypes(registry: TileTypeRegistry) {
        registry.register(TileTypeDefinition(id = ExamplePairingIds.INVERTED_ONE_MAN))
    }

    /** 登記範例的設定、牌河、公開標記與動作網路格式。 */
    override fun registerNetworkDtos(registries: NetworkDtoRegistries) = registerExampleNetworkDtos(registries)

    /** 登記獨立的保存格式，供權威存檔與歷史沿用。 */
    override fun registerPersistenceDtos(registries: PersistenceRegistries) = registerExamplePersistenceDtos(registries)

    /** 登記不重新計算規則的摸切策略。 */
    override fun registerAiStrategies(registry: MahjongAiStrategyRegistry) {
        registry.register(MahjongAiStrategyDefinition(key = ExampleAiStrategyIds.TSUMOGIRI) { ExampleTsumogiriAiStrategy() })
    }

    /** 登記示範宣告的命令建立方式。 */
    override fun registerGameActionCommandFactories(registry: ExtensionGameActionCommandFactoryRegistry) = registerExamplePairingDeclarationFactory(registry)

    /** 登記示範宣告的權威處理器。 */
    override fun registerGameCommandHandlers(registry: ExtensionGameCommandExecutorRegistry) = registerExamplePairingDeclarationHandler(registry)

    /** 登記依換牌選項決定是否啟用的規則選擇。 */
    override fun registerRuleDecisionResolvers(registry: RuleDecisionResolverRegistry) {
        registry.register(ExamplePairingRuleDecisionResolver())
    }

    /** 登記只描述實際付款點數的結算詳情。 */
    override fun registerWinSettlementDetailResolvers(registry: WinSettlementDetailResolverRegistry) {
        registry.register(
            definition = WinSettlementDetailResolverDefinition(
                ruleModuleId = ExamplePairingIds.RULE_MODULE,
                resolver = ExamplePairingWinSettlementDetailResolver,
            ),
        )
    }
}
