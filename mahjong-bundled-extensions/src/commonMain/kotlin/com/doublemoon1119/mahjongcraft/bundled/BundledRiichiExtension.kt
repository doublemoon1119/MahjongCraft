package com.doublemoon1119.mahjongcraft.bundled

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.ai.riichi.RiichiDeclarationAiHandler
import com.doublemoon1119.mahjongcraft.ai.riichi.RiichiOpponentModel
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.common.di.RiichiWinCelebrationCueResolver
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiDiscardPileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiDynamicStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiExhaustiveDrawReasonDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiGameActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiGameCommandDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiGameLengthDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiScoreConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.toRiichiDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.BuiltInDiscardPilePersistenceKeys
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.BuiltinHistoryReplayDiscardCodecs
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.RiichiDiscardPilePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.RiichiDynamicStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.RiichiExhaustiveDrawReasonPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.RiichiExhaustiveDrawReasonPersistenceValue
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.RiichiGameActionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.RiichiPendingKanDoraRevealPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.RiichiPlayerStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.RiichiRuleConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.DeclareRiichiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiNagashiManganOutcomeResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiSuuchaRiichiResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiSuufonRendaResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiSuukanNagareResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiWinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.base.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPendingKanDoraReveal
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeDefinition
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import kotlin.uuid.Uuid

/**
 * 隨 MahjongCraft 發布的日麻 extension；日麻在平台無關層的所有登記都寫在這裡，透過與第三方相同的 [MahjongExtension] 回呼登記。
 *
 * 包含規則模組、赤五牌種、規則設定與規則狀態的網路及存檔格式、牌譜牌河格式、立直等擴充動作的格式、
 * AI 與命令處理、對手模型、途中流局與流局滿貫判定、胡牌詳情，以及役滿演出提示。
 */
object BundledRiichiExtension : MahjongExtension {
    override val id: String = MahjongCraftMetadata.id("riichi")

    override fun registerRuleModules(registry: MahjongModuleRegistry) {
        registry.register(RiichiRuleConfig::class, BuiltInRuleModuleIds.RIICHI) { config, id -> RiichiRuleModule(id, config) }
    }

    override fun registerTileTypes(registry: TileTypeRegistry) {
        RiichiTileTypes.ALL.forEach { id -> registry.register(TileTypeDefinition(id)) }
    }

    override fun registerWinCelebrationCueResolvers(registry: WinCelebrationCueResolverRegistry) {
        registry.register(BuiltInRuleModuleIds.RIICHI, RiichiWinCelebrationCueResolver)
    }

    override fun registerNetworkDtos(registries: NetworkDtoRegistries) {
        registries.ruleConfig.register(
            RiichiRuleConfig::class,
            RiichiRuleConfigDto::class,
            RiichiRuleConfigDto.serializer(),
            { it.toRiichiDto(registries) },
            { it.toDomain(registries) },
        )
        registries.scoreConfig.register(
            RiichiScoreConfig::class,
            RiichiScoreConfigDto::class,
            RiichiScoreConfigDto.serializer(),
            RiichiScoreConfig::toRiichiDto,
            RiichiScoreConfigDto::toDomain,
        )
        registries.gameLength.register(
            RiichiGameLength.OneGame::class,
            RiichiGameLengthDto.OneGame::class,
            RiichiGameLengthDto.OneGame.serializer(),
            { RiichiGameLengthDto.OneGame },
            { RiichiGameLength.OneGame },
        )
        registries.gameLength.register(
            RiichiGameLength.East::class,
            RiichiGameLengthDto.East::class,
            RiichiGameLengthDto.East.serializer(),
            { RiichiGameLengthDto.East },
            { RiichiGameLength.East },
        )
        registries.gameLength.register(
            RiichiGameLength.TwoWinds::class,
            RiichiGameLengthDto.TwoWinds::class,
            RiichiGameLengthDto.TwoWinds.serializer(),
            { RiichiGameLengthDto.TwoWinds },
            { RiichiGameLength.TwoWinds },
        )
        registries.dynamicRuleState.register(
            RiichiDynamicState::class,
            RiichiDynamicStateDto::class,
            RiichiDynamicStateDto.serializer(),
            RiichiDynamicState::toRiichiDto,
            RiichiDynamicStateDto::toDomain,
        )
        registries.playerRuleState.register(
            RiichiPlayerState::class,
            RiichiPlayerStateDto::class,
            RiichiPlayerStateDto.serializer(),
            RiichiPlayerState::toRiichiDto,
            RiichiPlayerStateDto::toDomain,
        )
        registries.discardPile.register(
            RiichiDiscardPile::class,
            RiichiDiscardPileDto::class,
            RiichiDiscardPileDto.serializer(),
            RiichiDiscardPile::toRiichiDto,
            RiichiDiscardPileDto::toDomain,
        )
        registries.exhaustiveDrawReason.register(
            RiichiExhaustiveDrawReason.Normal::class,
            RiichiExhaustiveDrawReasonDto.Normal::class,
            RiichiExhaustiveDrawReasonDto.Normal.serializer(),
            { RiichiExhaustiveDrawReasonDto.Normal },
            { RiichiExhaustiveDrawReason.Normal },
        )
        registries.exhaustiveDrawReason.register(
            RiichiExhaustiveDrawReason.KyuushuKyuuhai::class,
            RiichiExhaustiveDrawReasonDto.KyuushuKyuuhai::class,
            RiichiExhaustiveDrawReasonDto.KyuushuKyuuhai.serializer(),
            { RiichiExhaustiveDrawReasonDto.KyuushuKyuuhai },
            { RiichiExhaustiveDrawReason.KyuushuKyuuhai },
        )
        registries.exhaustiveDrawReason.register(
            RiichiExhaustiveDrawReason.SuufonRenda::class,
            RiichiExhaustiveDrawReasonDto.SuufonRenda::class,
            RiichiExhaustiveDrawReasonDto.SuufonRenda.serializer(),
            { RiichiExhaustiveDrawReasonDto.SuufonRenda },
            { RiichiExhaustiveDrawReason.SuufonRenda },
        )
        registries.exhaustiveDrawReason.register(
            RiichiExhaustiveDrawReason.SuukanNagare::class,
            RiichiExhaustiveDrawReasonDto.SuukanNagare::class,
            RiichiExhaustiveDrawReasonDto.SuukanNagare.serializer(),
            { RiichiExhaustiveDrawReasonDto.SuukanNagare },
            { RiichiExhaustiveDrawReason.SuukanNagare },
        )
        registries.exhaustiveDrawReason.register(
            RiichiExhaustiveDrawReason.SuuchaRiichi::class,
            RiichiExhaustiveDrawReasonDto.SuuchaRiichi::class,
            RiichiExhaustiveDrawReasonDto.SuuchaRiichi.serializer(),
            { RiichiExhaustiveDrawReasonDto.SuuchaRiichi },
            { RiichiExhaustiveDrawReason.SuuchaRiichi },
        )
        registries.exhaustiveDrawReason.register(
            RiichiExhaustiveDrawReason.SanchaHou::class,
            RiichiExhaustiveDrawReasonDto.SanchaHou::class,
            RiichiExhaustiveDrawReasonDto.SanchaHou.serializer(),
            { RiichiExhaustiveDrawReasonDto.SanchaHou },
            { RiichiExhaustiveDrawReason.SanchaHou },
        )
        registries.extensionGameAction.register(
            RiichiGameAction.Riichi::class,
            RiichiGameActionDto::class,
            RiichiGameActionDto.serializer(),
            { RiichiGameActionDto },
            { RiichiGameAction.Riichi },
        )
        registries.extensionGameCommand.register(
            RiichiGameCommand::class,
            RiichiGameCommandDto::class,
            RiichiGameCommandDto.serializer(),
            { RiichiGameCommandDto(it.tileId.toString()) },
            { RiichiGameCommand(Uuid.parse(it.tileId)) },
        )
    }

    override fun registerPersistenceDtos(registries: PersistenceRegistries) {
        registries.ruleConfigs.register(
            typeKey = "builtin:riichi_rule_config",
            domainClass = RiichiRuleConfig::class,
            serializer = RiichiRuleConfigPersistenceDto.serializer(),
            toDto = RiichiRuleConfig::toPersistenceDto,
            toDomain = RiichiRuleConfigPersistenceDto::toDomain,
        )
        registries.discardPiles.register(
            typeKey = BuiltInDiscardPilePersistenceKeys.RIICHI,
            domainClass = RiichiDiscardPile::class,
            serializer = RiichiDiscardPilePersistenceDto.serializer(),
            toDto = RiichiDiscardPile::toPersistenceDto,
            toDomain = RiichiDiscardPilePersistenceDto::toDomain,
        )
        registries.playerRuleStates.register(
            typeKey = "builtin:riichi_player_state",
            domainClass = RiichiPlayerState::class,
            serializer = RiichiPlayerStatePersistenceDto.serializer(),
            toDto = RiichiPlayerState::toPersistenceDto,
            toDomain = RiichiPlayerStatePersistenceDto::toDomain,
        )
        registries.dynamicRuleStates.register(
            typeKey = "builtin:riichi_dynamic_state",
            domainClass = RiichiDynamicState::class,
            serializer = RiichiDynamicStatePersistenceDto.serializer(),
            toDto = {
                RiichiDynamicStatePersistenceDto(
                    it.riichiStickCount,
                    it.completedSupplementalDrawCount,
                    it.revealedKanDoraCount,
                    it.pendingKanDoraReveals.map(RiichiPendingKanDoraReveal::toPersistenceDto),
                )
            },
            toDomain = {
                RiichiDynamicState(
                    it.riichiStickCount,
                    it.completedSupplementalDrawCount,
                    it.revealedKanDoraCount,
                    it.pendingKanDoraReveals.map(RiichiPendingKanDoraRevealPersistenceDto::toDomain),
                )
            },
        )
        registries.exhaustiveDrawReasons.register(
            typeKey = "riichi.exhaustive_draw.normal",
            domainClass = RiichiExhaustiveDrawReason.Normal::class,
            serializer = RiichiExhaustiveDrawReasonPersistenceDto.serializer(),
            toDto = { RiichiExhaustiveDrawReasonPersistenceDto(RiichiExhaustiveDrawReasonPersistenceValue.NORMAL) },
            toDomain = { RiichiExhaustiveDrawReason.Normal },
        )
        registries.exhaustiveDrawReasons.register(
            typeKey = "riichi.exhaustive_draw.kyuushu_kyuuhai",
            domainClass = RiichiExhaustiveDrawReason.KyuushuKyuuhai::class,
            serializer = RiichiExhaustiveDrawReasonPersistenceDto.serializer(),
            toDto = { RiichiExhaustiveDrawReasonPersistenceDto(RiichiExhaustiveDrawReasonPersistenceValue.KYUUSHU_KYUUHAI) },
            toDomain = { RiichiExhaustiveDrawReason.KyuushuKyuuhai },
        )
        registries.exhaustiveDrawReasons.register(
            typeKey = "riichi.exhaustive_draw.suufon_renda",
            domainClass = RiichiExhaustiveDrawReason.SuufonRenda::class,
            serializer = RiichiExhaustiveDrawReasonPersistenceDto.serializer(),
            toDto = { RiichiExhaustiveDrawReasonPersistenceDto(RiichiExhaustiveDrawReasonPersistenceValue.SUUFON_RENDA) },
            toDomain = { RiichiExhaustiveDrawReason.SuufonRenda },
        )
        registries.exhaustiveDrawReasons.register(
            typeKey = "riichi.exhaustive_draw.suukan_nagare",
            domainClass = RiichiExhaustiveDrawReason.SuukanNagare::class,
            serializer = RiichiExhaustiveDrawReasonPersistenceDto.serializer(),
            toDto = { RiichiExhaustiveDrawReasonPersistenceDto(RiichiExhaustiveDrawReasonPersistenceValue.SUUKAN_NAGARE) },
            toDomain = { RiichiExhaustiveDrawReason.SuukanNagare },
        )
        registries.exhaustiveDrawReasons.register(
            typeKey = "riichi.exhaustive_draw.suucha_riichi",
            domainClass = RiichiExhaustiveDrawReason.SuuchaRiichi::class,
            serializer = RiichiExhaustiveDrawReasonPersistenceDto.serializer(),
            toDto = { RiichiExhaustiveDrawReasonPersistenceDto(RiichiExhaustiveDrawReasonPersistenceValue.SUUCHA_RIICHI) },
            toDomain = { RiichiExhaustiveDrawReason.SuuchaRiichi },
        )
        registries.exhaustiveDrawReasons.register(
            typeKey = "riichi.exhaustive_draw.sancha_hou",
            domainClass = RiichiExhaustiveDrawReason.SanchaHou::class,
            serializer = RiichiExhaustiveDrawReasonPersistenceDto.serializer(),
            toDto = { RiichiExhaustiveDrawReasonPersistenceDto(RiichiExhaustiveDrawReasonPersistenceValue.SANCHA_HOU) },
            toDomain = { RiichiExhaustiveDrawReason.SanchaHou },
        )
        registries.extensionGameActions.register(
            typeKey = BuiltInGameActionIds.RIICHI,
            domainClass = RiichiGameAction.Riichi::class,
            serializer = RiichiGameActionPersistenceDto.serializer(),
            toDto = { RiichiGameActionPersistenceDto },
            toDomain = { RiichiGameAction.Riichi },
        )
    }

    override fun registerHistoryReplayProjections(registry: HistoryReplayProjectionRegistry) {
        registry.registerDiscard(BuiltInDiscardPilePersistenceKeys.RIICHI, BuiltinHistoryReplayDiscardCodecs.riichi)
    }

    override fun registerGameActionAiHandlers(registry: ExtensionGameActionAiRegistry) {
        registry.register(RiichiGameAction.Riichi::class, RiichiDeclarationAiHandler)
    }

    override fun registerOpponentModels(registry: OpponentModelRegistry) {
        registry.register(BuiltInRuleModuleIds.RIICHI) { module, depth -> RiichiOpponentModel(rules = module.createPositionRules(), readingDepth = depth) }
    }

    override fun registerGameActionCommandFactories(registry: ExtensionGameActionCommandFactoryRegistry) {
        registry.register(RiichiGameAction.Riichi::class) { _, selectedTileIds ->
            selectedTileIds.singleOrNull()?.let(::RiichiGameCommand)
        }
    }

    /** 每個執行環境以自己的流程服務建立 [DeclareRiichiUseCase]。 */
    override fun registerGameCommandHandlers(registry: ExtensionGameCommandExecutorRegistry) {
        registry.register(RiichiGameCommand::class) { context ->
            val declareRiichiUseCase = DeclareRiichiUseCase(
                gameRepository = context.gameRepository,
                moduleRegistry = context.moduleRegistry,
                snapshotSynchronizer = context.snapshotSynchronizer,
                handSortPreferenceStore = context.handSortPreferenceStore,
                postActionExhaustiveDrawResolverRegistry = context.postActionExhaustiveDrawResolverRegistry,
                eventPublisher = context.eventPublisher,
                presentationPublisher = context.presentationPublisher,
            )
            object : ExtensionGameCommandHandler<RiichiGameCommand> {
                override suspend fun execute(
                    gameId: Uuid,
                    playerId: Uuid,
                    command: RiichiGameCommand,
                ): Outcome<Unit, GameError> = declareRiichiUseCase(gameId, playerId, command.tileId)
            }
        }
    }

    override fun registerPostReactionRoundOutcomeResolvers(registry: PostReactionRoundOutcomeResolverRegistry) {
        registry.register(RiichiNagashiManganOutcomeResolver())
    }

    /** 主動觸發的途中流局：四風連打、四家立直、四槓散了。 */
    override fun registerPostActionExhaustiveDrawResolvers(registry: PostActionExhaustiveDrawResolverRegistry) {
        registry.register(RiichiSuufonRendaResolver())
        registry.register(RiichiSuuchaRiichiResolver())
        registry.register(RiichiSuukanNagareResolver())
    }

    override fun registerWinSettlementDetailResolvers(registry: WinSettlementDetailResolverRegistry) {
        registry.register(BuiltInRuleModuleIds.RIICHI, RiichiWinSettlementDetailResolver)
    }
}
