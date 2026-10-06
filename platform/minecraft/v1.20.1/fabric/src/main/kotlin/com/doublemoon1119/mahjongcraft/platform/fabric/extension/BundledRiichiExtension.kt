package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.ai.riichi.registerRiichiGameActionHandler
import com.doublemoon1119.mahjongcraft.ai.riichi.registerRiichiOpponentModel
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.registerRiichiGameActionDtos
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.registerRiichiGameActionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.DeclareRiichiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiGameActionCommandFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiGameCommandHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiNagashiManganOutcomeResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiPostActionExhaustiveDrawResolvers
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiWinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 隨 MahjongCraft 發布的日麻 extension，透過與第三方相同的 [MahjongExtension] 回呼登記日麻專屬整合。
 *
 * 這裡登記立直等擴充動作的網路與存檔格式、AI 與命令處理、對手模型、途中流局與流局滿貫判定，以及胡牌詳情。
 * 日麻規則模組、牌種與規則設定 DTO 由內建規則的登記另外處理，不經過這個 extension。
 *
 * @property moduleRegistry 立直動作的 AI handler 依對局設定取得規則模組。
 * @property declareRiichiUseCase 立直命令 handler 執行宣告使用的用例。
 */
internal class BundledRiichiExtension(
    private val moduleRegistry: MahjongModuleRegistry,
    private val declareRiichiUseCase: DeclareRiichiUseCase,
) : MahjongExtension {
    override val id: String = MahjongCraftMetadata.id("riichi")

    override fun registerRuleModules(registry: MahjongModuleRegistry) = Unit

    override fun registerNetworkDtos(registries: NetworkDtoRegistries) {
        registries.registerRiichiGameActionDtos()
    }

    override fun registerPersistenceDtos(registries: PersistenceRegistries) {
        registries.extensionGameActions.registerRiichiGameActionPersistenceDto()
    }

    override fun registerGameActionAiHandlers(registry: ExtensionGameActionAiRegistry) {
        registry.registerRiichiGameActionHandler(moduleRegistry)
    }

    override fun registerOpponentModels(registry: OpponentModelRegistry) {
        registry.registerRiichiOpponentModel()
    }

    override fun registerGameActionCommandFactories(registry: ExtensionGameActionCommandFactoryRegistry) {
        registry.registerRiichiGameActionCommandFactory()
    }

    override fun registerGameCommandHandlers(registry: ExtensionGameCommandExecutorRegistry) {
        registry.registerRiichiGameCommandHandler(declareRiichiUseCase)
    }

    override fun registerPostReactionRoundOutcomeResolvers(registry: PostReactionRoundOutcomeResolverRegistry) {
        registry.registerRiichiNagashiManganOutcomeResolver()
    }

    override fun registerPostActionExhaustiveDrawResolvers(registry: PostActionExhaustiveDrawResolverRegistry) {
        registry.registerRiichiPostActionExhaustiveDrawResolvers()
    }

    override fun registerWinSettlementDetailResolvers(registry: WinSettlementDetailResolverRegistry) {
        registry.registerRiichiWinSettlementDetailResolver()
    }
}
