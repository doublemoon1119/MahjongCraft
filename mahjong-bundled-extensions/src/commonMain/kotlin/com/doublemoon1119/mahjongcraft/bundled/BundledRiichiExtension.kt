package com.doublemoon1119.mahjongcraft.bundled

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.ai.riichi.registerRiichiGameActionHandler
import com.doublemoon1119.mahjongcraft.ai.riichi.registerRiichiOpponentModel
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.common.di.registerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.flow.common.di.registerRiichiTileTypes
import com.doublemoon1119.mahjongcraft.flow.common.di.registerRiichiWinCelebrationCueResolver
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.registerRiichiGameActionDtos
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.registerRiichiRuleDtos
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.registerRiichiHistoryReplayProjections
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.registerRiichiPersistenceDtos
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.registerRiichiGameActionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiGameActionCommandFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiGameCommandHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiNagashiManganOutcomeResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiPostActionExhaustiveDrawResolvers
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiWinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 隨 MahjongCraft 發布的日麻 extension，透過與第三方相同的 [MahjongExtension] 回呼登記日麻的平台無關整合。
 *
 * 包含規則模組、赤五牌種、規則設定與規則狀態的網路及存檔格式、牌譜牌河格式、立直等擴充動作的格式、
 * AI 與命令處理、對手模型、途中流局與流局滿貫判定、胡牌詳情，以及役滿演出提示。
 */
object BundledRiichiExtension : MahjongExtension {
    override val id: String = MahjongCraftMetadata.id("riichi")

    override fun registerRuleModules(registry: MahjongModuleRegistry) {
        registry.registerRiichiRuleModule()
    }

    override fun registerTileTypes(registry: TileTypeRegistry) {
        registry.registerRiichiTileTypes()
    }

    override fun registerWinCelebrationCueResolvers(registry: WinCelebrationCueResolverRegistry) {
        registry.registerRiichiWinCelebrationCueResolver()
    }

    override fun registerNetworkDtos(registries: NetworkDtoRegistries) {
        registries.registerRiichiRuleDtos()
        registries.registerRiichiGameActionDtos()
    }

    override fun registerPersistenceDtos(registries: PersistenceRegistries) {
        registries.registerRiichiPersistenceDtos()
        registries.extensionGameActions.registerRiichiGameActionPersistenceDto()
    }

    override fun registerHistoryReplayProjections(registry: HistoryReplayProjectionRegistry) {
        registry.registerRiichiHistoryReplayProjections()
    }

    override fun registerGameActionAiHandlers(registry: ExtensionGameActionAiRegistry) {
        registry.registerRiichiGameActionHandler()
    }

    override fun registerOpponentModels(registry: OpponentModelRegistry) {
        registry.registerRiichiOpponentModel()
    }

    override fun registerGameActionCommandFactories(registry: ExtensionGameActionCommandFactoryRegistry) {
        registry.registerRiichiGameActionCommandFactory()
    }

    override fun registerGameCommandHandlers(registry: ExtensionGameCommandExecutorRegistry) {
        registry.registerRiichiGameCommandHandler()
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
