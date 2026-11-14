package com.doublemoon1119.mahjongcraft.bundled

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiDefinition
import com.doublemoon1119.mahjongcraft.ai.api.registration.ExtensionGameActionAiRegistrar
import com.doublemoon1119.mahjongcraft.ai.api.registration.OpponentModelRegistrar
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelDefinition
import com.doublemoon1119.mahjongcraft.ai.taiwan.TaiwanOpponentModel
import com.doublemoon1119.mahjongcraft.ai.taiwan.TaiwanReadyDeclarationAiHandler
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanDeclareReadyCommand
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.network.dto.api.registration.NetworkDtoRegistrars
import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewNetworkDefinition
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.DtoDefinition
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanDeclareReadyCommandDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanExhaustiveDrawReasonDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanGameActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanGameLengthDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanPassedWinViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanReadyDeclarationViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanScoreConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.toTaiwanDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration.PersistenceRegistrars
import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentPersistenceDefinition
import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentUnitPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoDefinition
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.BuiltInDiscardPilePersistenceKeys
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.TaiwanDiscardPilePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.TaiwanExhaustiveDrawReasonPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.TaiwanExhaustiveDrawReasonPersistenceValue
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.TaiwanGameActionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.TaiwanPaoLiabilityPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.TaiwanPendingFlowerFollowUpPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.TaiwanReadyDeclarationPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.TaiwanRuleConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.ExtensionGameActionCommandFactoryRegistrar
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.ExtensionGameCommandExecutorRegistrar
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.PostActionExhaustiveDrawResolverRegistrar
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.RuleDecisionResolverRegistrar
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.WinSettlementDetailResolverRegistrar
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryDefinition
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandHandlerDefinition
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverDefinition
import com.doublemoon1119.mahjongcraft.flow.server.game.taiwan.TaiwanFlowerRuleDecisionResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.taiwan.TaiwanFourKansResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.taiwan.TaiwanFourWindsResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.taiwan.TaiwanWinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar
import com.doublemoon1119.mahjongcraft.logic.api.registration.MahjongModuleRegistrar
import com.doublemoon1119.mahjongcraft.logic.api.registration.TileTypeRegistrar
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TAIWAN_DECLARE_READY_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerComponentViews
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerComponents
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTableComponents
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.registerTaiwanPlayerComponents
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.registerTaiwanTableComponents
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeDefinition
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import kotlin.uuid.Uuid

/**
 * 隨 MahjongCraft 發布的台麻 extension；台麻在平台無關層的所有登記都寫在這裡，透過與第三方相同的 [MahjongExtension] 回呼登記。
 *
 * 包含規則模組、花牌牌種、規則設定與牌河的網路及存檔格式，以及牌譜牌河格式。
 */
object BundledTaiwanExtension : MahjongExtension {
    override val id: String = MahjongCraftMetadata.id("taiwan")

    override fun registerRuleModules(registry: MahjongModuleRegistrar) {
        registry.register(MahjongModuleDefinition(TaiwanRuleConfig::class, BuiltInRuleModuleIds.TAIWAN) { config, id -> TaiwanRuleModule(id, config) })
    }

    override fun registerTileTypes(registry: TileTypeRegistrar) {
        TaiwanTileTypes.ALL.forEach { id -> registry.register(TileTypeDefinition(id)) }
    }

    override fun registerComponents(registry: ComponentRegistrar) {
        registerTaiwanTableComponents(registry)
        registerTaiwanPlayerComponents(registry)
    }

    override fun registerGameActionAiHandlers(registry: ExtensionGameActionAiRegistrar) {
        registry.register(ExtensionGameActionAiDefinition(TaiwanGameAction.DeclareReady::class, TaiwanReadyDeclarationAiHandler))
    }

    override fun registerOpponentModels(registry: OpponentModelRegistrar) {
        registry.register(
            definition = OpponentModelDefinition(BuiltInRuleModuleIds.TAIWAN) { module, depth ->
                TaiwanOpponentModel(scoreConfig = (module.config as TaiwanRuleConfig).scoreConfig, readingDepth = depth)
            },
        )
    }

    override fun registerGameActionCommandFactories(registry: ExtensionGameActionCommandFactoryRegistrar) {
        registry.register(
            definition = ExtensionGameActionCommandFactoryDefinition(TaiwanGameAction.DeclareReady::class) { _, selectedTileIds ->
                selectedTileIds.singleOrNull()?.let(::TaiwanDeclareReadyCommand)
            },
        )
    }

    /** 宣告聽牌由共用的宣告並打牌流程處理。 */
    override fun registerGameCommandHandlers(registry: ExtensionGameCommandExecutorRegistrar) {
        registry.register(
            definition = ExtensionGameCommandHandlerDefinition(TaiwanDeclareReadyCommand::class) { context ->
                object : ExtensionGameCommandHandler<TaiwanDeclareReadyCommand> {
                    override suspend fun execute(
                        gameId: Uuid,
                        playerId: Uuid,
                        command: TaiwanDeclareReadyCommand,
                    ): Outcome<Unit, GameError> = context.declareAndDiscardUseCase(
                        gameId = gameId,
                        playerId = playerId,
                        action = TAIWAN_DECLARE_READY_ACTION,
                        tileId = command.tileId,
                    )
                }
            },
        )
    }

    override fun registerRuleDecisionResolvers(registry: RuleDecisionResolverRegistrar) {
        registry.register(TaiwanFlowerRuleDecisionResolver)
    }

    override fun registerWinSettlementDetailResolvers(registry: WinSettlementDetailResolverRegistrar) {
        registry.register(WinSettlementDetailResolverDefinition(BuiltInRuleModuleIds.TAIWAN, TaiwanWinSettlementDetailResolver))
    }

    /** 登記固定的四槓流局與可選的四風連打判定。 */
    override fun registerPostActionExhaustiveDrawResolvers(registry: PostActionExhaustiveDrawResolverRegistrar) {
        registry.register(TaiwanFourKansResolver())
        registry.register(TaiwanFourWindsResolver())
    }

    override fun registerNetworkDtos(registries: NetworkDtoRegistrars) {
        registries.componentViews.register(
            definition = ComponentViewNetworkDefinition(
                viewType = TaiwanPlayerComponentViews.READY_DECLARATION,
                dtoClass = TaiwanReadyDeclarationViewDto::class,
                serializer = TaiwanReadyDeclarationViewDto.serializer(),
                toDto = { it.toDto() },
                toValue = { it.toDomain() },
            ),
        )
        registries.componentViews.register(
            definition = ComponentViewNetworkDefinition(
                viewType = TaiwanPlayerComponentViews.PASSED_WIN,
                dtoClass = TaiwanPassedWinViewDto::class,
                serializer = TaiwanPassedWinViewDto.serializer(),
                toDto = { TaiwanPassedWinViewDto },
                toValue = {},
            ),
        )
        registries.extensionGameAction.register(
            definition = DtoDefinition(
                domainClass = TaiwanGameAction.RevealFlower::class,
                dtoClass = TaiwanGameActionDto.RevealFlower::class,
                serializer = TaiwanGameActionDto.RevealFlower.serializer(),
                toDto = { TaiwanGameActionDto.RevealFlower },
                toDomain = { TaiwanGameAction.RevealFlower },
            ),
        )
        registries.extensionGameAction.register(
            definition = DtoDefinition(
                domainClass = TaiwanGameAction.DeclareReady::class,
                dtoClass = TaiwanGameActionDto.DeclareReady::class,
                serializer = TaiwanGameActionDto.DeclareReady.serializer(),
                toDto = { TaiwanGameActionDto.DeclareReady },
                toDomain = { TaiwanGameAction.DeclareReady },
            ),
        )
        registries.extensionGameCommand.register(
            definition = DtoDefinition(
                domainClass = TaiwanDeclareReadyCommand::class,
                dtoClass = TaiwanDeclareReadyCommandDto::class,
                serializer = TaiwanDeclareReadyCommandDto.serializer(),
                toDto = { TaiwanDeclareReadyCommandDto(it.tileId.toString()) },
                toDomain = { TaiwanDeclareReadyCommand(Uuid.parse(it.tileId)) },
            ),
        )
        registries.ruleConfig.register(
            definition = DtoDefinition(
                domainClass = TaiwanRuleConfig::class,
                dtoClass = TaiwanRuleConfigDto::class,
                serializer = TaiwanRuleConfigDto.serializer(),
                toDto = { value, converter -> value.toTaiwanDto(converter) },
                toDomain = { dto, converter -> dto.toDomain(converter) },
            ),
        )
        registries.scoreConfig.register(
            definition = DtoDefinition(
                domainClass = TaiwanScoreConfig::class,
                dtoClass = TaiwanScoreConfigDto::class,
                serializer = TaiwanScoreConfigDto.serializer(),
                toDto = TaiwanScoreConfig::toTaiwanDto,
                toDomain = TaiwanScoreConfigDto::toDomain,
            ),
        )
        registries.gameLength.register(
            definition = DtoDefinition(
                domainClass = TaiwanGameLength.OneGame::class,
                dtoClass = TaiwanGameLengthDto.OneGame::class,
                serializer = TaiwanGameLengthDto.OneGame.serializer(),
                toDto = { TaiwanGameLengthDto.OneGame },
                toDomain = { TaiwanGameLength.OneGame },
            ),
        )
        registries.exhaustiveDrawReason.register(
            definition = DtoDefinition(
                domainClass = TaiwanExhaustiveDrawReason.Normal::class,
                dtoClass = TaiwanExhaustiveDrawReasonDto.Normal::class,
                serializer = TaiwanExhaustiveDrawReasonDto.Normal.serializer(),
                toDto = { TaiwanExhaustiveDrawReasonDto.Normal },
                toDomain = { TaiwanExhaustiveDrawReason.Normal },
            ),
        )
        registries.exhaustiveDrawReason.register(
            definition = DtoDefinition(
                domainClass = TaiwanExhaustiveDrawReason.NoSupplementalTile::class,
                dtoClass = TaiwanExhaustiveDrawReasonDto.NoSupplementalTile::class,
                serializer = TaiwanExhaustiveDrawReasonDto.NoSupplementalTile.serializer(),
                toDto = { TaiwanExhaustiveDrawReasonDto.NoSupplementalTile },
                toDomain = { TaiwanExhaustiveDrawReason.NoSupplementalTile },
            ),
        )
        registries.exhaustiveDrawReason.register(
            definition = DtoDefinition(
                domainClass = TaiwanExhaustiveDrawReason.FourKans::class,
                dtoClass = TaiwanExhaustiveDrawReasonDto.FourKans::class,
                serializer = TaiwanExhaustiveDrawReasonDto.FourKans.serializer(),
                toDto = { TaiwanExhaustiveDrawReasonDto.FourKans },
                toDomain = { TaiwanExhaustiveDrawReason.FourKans },
            ),
        )
        registries.exhaustiveDrawReason.register(
            definition = DtoDefinition(
                domainClass = TaiwanExhaustiveDrawReason.FourWinds::class,
                dtoClass = TaiwanExhaustiveDrawReasonDto.FourWinds::class,
                serializer = TaiwanExhaustiveDrawReasonDto.FourWinds.serializer(),
                toDto = { TaiwanExhaustiveDrawReasonDto.FourWinds },
                toDomain = { TaiwanExhaustiveDrawReason.FourWinds },
            ),
        )
        registries.gameLength.register(
            definition = DtoDefinition(
                domainClass = TaiwanGameLength.East::class,
                dtoClass = TaiwanGameLengthDto.East::class,
                serializer = TaiwanGameLengthDto.East.serializer(),
                toDto = { TaiwanGameLengthDto.East },
                toDomain = { TaiwanGameLength.East },
            ),
        )
        registries.gameLength.register(
            definition = DtoDefinition(
                domainClass = TaiwanGameLength.TwoWinds::class,
                dtoClass = TaiwanGameLengthDto.TwoWinds::class,
                serializer = TaiwanGameLengthDto.TwoWinds.serializer(),
                toDto = { TaiwanGameLengthDto.TwoWinds },
                toDomain = { TaiwanGameLength.TwoWinds },
            ),
        )
        registries.gameLength.register(
            definition = DtoDefinition(
                domainClass = TaiwanGameLength.FourWinds::class,
                dtoClass = TaiwanGameLengthDto.FourWinds::class,
                serializer = TaiwanGameLengthDto.FourWinds.serializer(),
                toDto = { TaiwanGameLengthDto.FourWinds },
                toDomain = { TaiwanGameLength.FourWinds },
            ),
        )
    }

    override fun registerPersistenceDtos(registries: PersistenceRegistrars) {
        registries.components.register(
            definition = ComponentPersistenceDefinition(
                component = TaiwanPlayerComponents.READY_DECLARATION,
                serializer = TaiwanReadyDeclarationPersistenceDto.serializer(),
                toDto = { it.toPersistenceDto() },
                toDomain = { it.toDomain() },
            ),
        )
        registries.components.register(
            definition = ComponentPersistenceDefinition(
                component = TaiwanPlayerComponents.PAO_LIABILITY,
                serializer = TaiwanPaoLiabilityPersistenceDto.serializer(),
                toDto = { it.toPersistenceDto() },
                toDomain = { it.toDomain() },
            ),
        )
        registries.components.register(
            definition = ComponentPersistenceDefinition(
                component = TaiwanPlayerComponents.PASSED_WIN,
                serializer = ComponentUnitPersistenceDto.serializer(),
                toDto = { ComponentUnitPersistenceDto },
                toDomain = {},
            ),
        )
        registries.extensionGameActions.register(
            definition = PersistenceDtoDefinition(
                typeKey = TaiwanGameAction.RevealFlower.id,
                domainClass = TaiwanGameAction.RevealFlower::class,
                serializer = TaiwanGameActionPersistenceDto.RevealFlower.serializer(),
                toDto = { TaiwanGameActionPersistenceDto.RevealFlower },
                toDomain = { TaiwanGameAction.RevealFlower },
            ),
        )
        registries.extensionGameActions.register(
            definition = PersistenceDtoDefinition(
                typeKey = TaiwanGameAction.DeclareReady.id,
                domainClass = TaiwanGameAction.DeclareReady::class,
                serializer = TaiwanGameActionPersistenceDto.DeclareReady.serializer(),
                toDto = { TaiwanGameActionPersistenceDto.DeclareReady },
                toDomain = { TaiwanGameAction.DeclareReady },
            ),
        )
        registries.components.register(
            definition = ComponentPersistenceDefinition(
                component = TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP,
                serializer = TaiwanPendingFlowerFollowUpPersistenceDto.serializer(),
                toDto = { it.toPersistenceDto() },
                toDomain = { it.toDomain() },
            ),
        )
        registries.ruleConfigs.register(
            definition = PersistenceDtoDefinition(
                typeKey = "mahjongcraft:taiwan/rule_config",
                domainClass = TaiwanRuleConfig::class,
                serializer = TaiwanRuleConfigPersistenceDto.serializer(),
                toDto = TaiwanRuleConfig::toPersistenceDto,
                toDomain = TaiwanRuleConfigPersistenceDto::toDomain,
                compactInReplay = true,
            ),
        )
        registries.discardPiles.register(
            definition = PersistenceDtoDefinition(
                typeKey = BuiltInDiscardPilePersistenceKeys.TAIWAN,
                domainClass = TaiwanDiscardPile::class,
                serializer = TaiwanDiscardPilePersistenceDto.serializer(),
                toDto = { value, converter -> value.toPersistenceDto(converter.components) },
                toDomain = { dto, converter -> dto.toDomain(converter.components) },
                compactInReplay = true,
            ),
        )
        registries.exhaustiveDrawReasons.register(
            definition = PersistenceDtoDefinition(
                typeKey = TaiwanExhaustiveDrawReason.Normal.id,
                domainClass = TaiwanExhaustiveDrawReason.Normal::class,
                serializer = TaiwanExhaustiveDrawReasonPersistenceDto.serializer(),
                toDto = { TaiwanExhaustiveDrawReasonPersistenceDto(TaiwanExhaustiveDrawReasonPersistenceValue.NORMAL) },
                toDomain = { TaiwanExhaustiveDrawReason.Normal },
            ),
        )
        registries.exhaustiveDrawReasons.register(
            definition = PersistenceDtoDefinition(
                typeKey = TaiwanExhaustiveDrawReason.NoSupplementalTile.id,
                domainClass = TaiwanExhaustiveDrawReason.NoSupplementalTile::class,
                serializer = TaiwanExhaustiveDrawReasonPersistenceDto.serializer(),
                toDto = { TaiwanExhaustiveDrawReasonPersistenceDto(TaiwanExhaustiveDrawReasonPersistenceValue.NO_SUPPLEMENTAL_TILE) },
                toDomain = { TaiwanExhaustiveDrawReason.NoSupplementalTile },
            ),
        )
        registries.exhaustiveDrawReasons.register(
            definition = PersistenceDtoDefinition(
                typeKey = TaiwanExhaustiveDrawReason.FourKans.id,
                domainClass = TaiwanExhaustiveDrawReason.FourKans::class,
                serializer = TaiwanExhaustiveDrawReasonPersistenceDto.serializer(),
                toDto = { TaiwanExhaustiveDrawReasonPersistenceDto(TaiwanExhaustiveDrawReasonPersistenceValue.FOUR_KANS) },
                toDomain = { TaiwanExhaustiveDrawReason.FourKans },
            ),
        )
        registries.exhaustiveDrawReasons.register(
            definition = PersistenceDtoDefinition(
                typeKey = TaiwanExhaustiveDrawReason.FourWinds.id,
                domainClass = TaiwanExhaustiveDrawReason.FourWinds::class,
                serializer = TaiwanExhaustiveDrawReasonPersistenceDto.serializer(),
                toDto = { TaiwanExhaustiveDrawReasonPersistenceDto(TaiwanExhaustiveDrawReasonPersistenceValue.FOUR_WINDS) },
                toDomain = { TaiwanExhaustiveDrawReason.FourWinds },
            ),
        )
    }
}
