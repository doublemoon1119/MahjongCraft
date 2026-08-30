package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.registerRiichiGameActionHandler
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.extension.MahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInTileTypes
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInWinCelebrationCueResolvers
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.registerBuiltInRuleConfigDtos
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.registerRiichiGameActionDtos
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.registerRiichiGameActionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.registerRiichiGameCommandHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.registerRiichiNagashiManganOutcomeResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.createBuiltInWinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareRiichiUseCase
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.DebugRoundPreparationResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.registerDebugWinRoundContinuationResolvers
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.registerRiichiGameActionDisplayName
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

/** Fabric Loader 用來發現 [MahjongExtension] 的 entrypoint key。 */
const val MAHJONG_EXTENSION_ENTRYPOINT: String = "${MinecraftModMetadata.MOD_ID}:extension"

/**
 * 發現並註冊 Fabric 環境中的第三方 [MahjongExtension]。
 *
 * 內建規則與 DTO 會先完成註冊，第三方 extension 隨後取得 runtime 實際使用的同一批 registry；
 * 全部成功後由 [MahjongExtensionRegistrar] 凍結 registry。
 */
object FabricMahjongExtensions {
    /** Fabric extension discovery 與註冊結果使用的 logger。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 透過 [FabricLoader] 發現 entrypoint，完成一次 runtime registry 初始化。 */
    fun initialize(
        moduleRegistry: MahjongModuleRegistry,
        tileTypeRegistry: TileTypeRegistry,
        networkRegistries: NetworkDtoRegistries,
        persistenceRegistries: PersistenceRegistries,
        minecraftTileAssetRegistry: MinecraftTileAssetRegistry,
        aiStrategyDisplayNameRegistry: AiStrategyDisplayNameRegistry,
        tileDisplayNameRegistry: TileDisplayNameRegistry,
        ruleModuleDisplayNameRegistry: RuleModuleDisplayNameRegistry,
        tileEmojiRegistry: TileEmojiRegistry,
        tileLabelRegistry: TileLabelRegistry,
        gameActionDisplayNameRegistry: GameActionDisplayNameRegistry = GameActionDisplayNameRegistryImpl(),
        exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry =
            ExhaustiveDrawReasonDisplayNameRegistryImpl(),
        roundPreparationDisplayNameRegistry: RoundPreparationDisplayNameRegistry =
            RoundPreparationDisplayNameRegistryImpl(),
        winCelebrationCueResolverRegistry: WinCelebrationCueResolverRegistry =
            WinCelebrationCueResolverRegistryImpl(),
        showcaseRegistry: WinCelebrationShowcaseRegistry =
            WinCelebrationShowcaseRegistryImpl(),
        winSettlementTemplateRegistry: WinSettlementPresentationTemplateRegistry =
            WinSettlementPresentationTemplateRegistryImpl(),
        matchSettlementTemplateRegistry: MatchSettlementPresentationTemplateRegistry =
            MatchSettlementPresentationTemplateRegistryImpl(),
        playerPortraitSourceRegistry: PlayerPortraitSourceRegistry = PlayerPortraitSourceRegistryImpl(),
        publicPlayerIndicatorDisplayRegistry: PublicPlayerIndicatorDisplayRegistry =
            PublicPlayerIndicatorDisplayRegistryImpl(),
        gameActionAiRegistry: ExtensionGameActionAiRegistry = ExtensionGameActionAiRegistry(),
        gameCommandRegistry: ExtensionGameCommandExecutorRegistry = ExtensionGameCommandExecutorRegistry(),
        postReactionRoundOutcomeResolverRegistry: PostReactionRoundOutcomeResolverRegistry =
            PostReactionRoundOutcomeResolverRegistry(),
        roundPreparationResolverRegistry: RoundPreparationResolverRegistry = RoundPreparationResolverRegistry(),
        winRoundContinuationResolverRegistry: WinRoundContinuationResolverRegistry =
            WinRoundContinuationResolverRegistry(),
        winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry =
            createBuiltInWinSettlementDetailResolverRegistry(),
        declareRiichiUseCase: DeclareRiichiUseCase,
        debugWinRoundContinuationState: DebugWinRoundContinuationState,
        minecraftEnvironment: MinecraftEnvironment,
    ) {
        try {
            val extensions = FabricLoader.getInstance()
                .getEntrypoints(MAHJONG_EXTENSION_ENTRYPOINT, MahjongExtension::class.java)
            val result = initialize(
                moduleRegistry = moduleRegistry,
                tileTypeRegistry = tileTypeRegistry,
                networkRegistries = networkRegistries,
                persistenceRegistries = persistenceRegistries,
                minecraftTileAssetRegistry = minecraftTileAssetRegistry,
                aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
                tileDisplayNameRegistry = tileDisplayNameRegistry,
                ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
                tileEmojiRegistry = tileEmojiRegistry,
                tileLabelRegistry = tileLabelRegistry,
                gameActionDisplayNameRegistry = gameActionDisplayNameRegistry,
                exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNameRegistry,
                roundPreparationDisplayNameRegistry = roundPreparationDisplayNameRegistry,
                winCelebrationCueResolverRegistry = winCelebrationCueResolverRegistry,
                showcaseRegistry = showcaseRegistry,
                winSettlementTemplateRegistry = winSettlementTemplateRegistry,
                matchSettlementTemplateRegistry = matchSettlementTemplateRegistry,
                playerPortraitSourceRegistry = playerPortraitSourceRegistry,
                publicPlayerIndicatorDisplayRegistry = publicPlayerIndicatorDisplayRegistry,
                gameActionAiRegistry = gameActionAiRegistry,
                gameCommandRegistry = gameCommandRegistry,
                postReactionRoundOutcomeResolverRegistry = postReactionRoundOutcomeResolverRegistry,
                roundPreparationResolverRegistry = roundPreparationResolverRegistry,
                winRoundContinuationResolverRegistry = winRoundContinuationResolverRegistry,
                winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
                declareRiichiUseCase = declareRiichiUseCase,
                debugWinRoundContinuationState = debugWinRoundContinuationState,
                minecraftEnvironment = minecraftEnvironment,
                extensions = extensions,
            )
            val extensionIds = extensions.map { it.id }
            logger.info(
                "Registered {} third-party Mahjong extension(s): {}",
                extensionIds.size,
                extensionIds,
            )
            logger.info(
                "Registered {} third-party Minecraft tile asset(s): {}",
                result.thirdPartyTileAssetKeys.size,
                result.thirdPartyTileAssetKeys,
            )
            logger.info(
                "Registered {} third-party AI strategy display name(s): {}",
                result.thirdPartyAiStrategyKeys.size,
                result.thirdPartyAiStrategyKeys,
            )
            logger.info(
                "Registered {} third-party tile display name(s): {}",
                result.thirdPartyTileDisplayNameKeys.size,
                result.thirdPartyTileDisplayNameKeys,
            )
            logger.info(
                "Registered {} third-party rule module display name(s): {}",
                result.thirdPartyRuleModuleDisplayNameKeys.size,
                result.thirdPartyRuleModuleDisplayNameKeys,
            )
            logger.info(
                "Registered {} third-party tile emoji(s): {}",
                result.thirdPartyTileEmojiKeys.size,
                result.thirdPartyTileEmojiKeys,
            )
            logger.info(
                "Registered {} third-party tile label(s): {}",
                result.thirdPartyTileLabelKeys.size,
                result.thirdPartyTileLabelKeys,
            )
        } catch (cause: Exception) {
            logger.error("Failed to initialize Mahjong extensions", cause)
            throw cause
        }
    }

    /**
     * 使用明確提供的 [extensions] 初始化，供平台測試驗證組裝順序。
     *
     * @return [MinecraftMahjongExtensionRegistrar.registerAndFreeze] 的登記結果，供呼叫端記錄診斷資訊。
     */
    internal fun initialize(
        moduleRegistry: MahjongModuleRegistry,
        tileTypeRegistry: TileTypeRegistry,
        networkRegistries: NetworkDtoRegistries,
        persistenceRegistries: PersistenceRegistries,
        minecraftTileAssetRegistry: MinecraftTileAssetRegistry,
        aiStrategyDisplayNameRegistry: AiStrategyDisplayNameRegistry,
        tileDisplayNameRegistry: TileDisplayNameRegistry,
        ruleModuleDisplayNameRegistry: RuleModuleDisplayNameRegistry,
        tileEmojiRegistry: TileEmojiRegistry,
        tileLabelRegistry: TileLabelRegistry,
        gameActionDisplayNameRegistry: GameActionDisplayNameRegistry = GameActionDisplayNameRegistryImpl(),
        exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry =
            ExhaustiveDrawReasonDisplayNameRegistryImpl(),
        roundPreparationDisplayNameRegistry: RoundPreparationDisplayNameRegistry =
            RoundPreparationDisplayNameRegistryImpl(),
        winCelebrationCueResolverRegistry: WinCelebrationCueResolverRegistry =
            WinCelebrationCueResolverRegistryImpl(),
        showcaseRegistry: WinCelebrationShowcaseRegistry =
            WinCelebrationShowcaseRegistryImpl(),
        winSettlementTemplateRegistry: WinSettlementPresentationTemplateRegistry =
            WinSettlementPresentationTemplateRegistryImpl(),
        matchSettlementTemplateRegistry: MatchSettlementPresentationTemplateRegistry =
            MatchSettlementPresentationTemplateRegistryImpl(),
        playerPortraitSourceRegistry: PlayerPortraitSourceRegistry = PlayerPortraitSourceRegistryImpl(),
        publicPlayerIndicatorDisplayRegistry: PublicPlayerIndicatorDisplayRegistry =
            PublicPlayerIndicatorDisplayRegistryImpl(),
        gameActionAiRegistry: ExtensionGameActionAiRegistry = ExtensionGameActionAiRegistry(),
        gameCommandRegistry: ExtensionGameCommandExecutorRegistry = ExtensionGameCommandExecutorRegistry(),
        postReactionRoundOutcomeResolverRegistry: PostReactionRoundOutcomeResolverRegistry =
            PostReactionRoundOutcomeResolverRegistry(),
        roundPreparationResolverRegistry: RoundPreparationResolverRegistry = RoundPreparationResolverRegistry(),
        winRoundContinuationResolverRegistry: WinRoundContinuationResolverRegistry =
            WinRoundContinuationResolverRegistry(),
        winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry =
            createBuiltInWinSettlementDetailResolverRegistry(),
        declareRiichiUseCase: DeclareRiichiUseCase,
        debugWinRoundContinuationState: DebugWinRoundContinuationState = DebugWinRoundContinuationState(),
        // 預設不註冊開發用的中途胡牌 resolver：這個多載的其他測試呼叫端只關心依賴圖，正式呼叫端
        // （MahjongCraftMod）會傳入真正的 MinecraftEnvironment。
        minecraftEnvironment: MinecraftEnvironment = NonDevelopmentEnvironment,
        extensions: Iterable<MahjongExtension>,
    ): MinecraftMahjongExtensionRegistrationResult {
        moduleRegistry.registerBuiltInRuleModules()
        tileTypeRegistry.registerBuiltInTileTypes()
        networkRegistries.registerBuiltInRuleConfigDtos()
        winCelebrationCueResolverRegistry.registerBuiltInWinCelebrationCueResolvers()
        registerBundledRiichiExtension(
            moduleRegistry = moduleRegistry,
            gameActionAiRegistry = gameActionAiRegistry,
            gameCommandRegistry = gameCommandRegistry,
            postReactionRoundOutcomeResolverRegistry = postReactionRoundOutcomeResolverRegistry,
            gameActionDisplayNameRegistry = gameActionDisplayNameRegistry,
            declareRiichiUseCase = declareRiichiUseCase,
            networkRegistries = networkRegistries,
            persistenceRegistries = persistenceRegistries,
        )
        // 開發環境限定：讓「胡牌後本局繼續」這條路徑在還沒有任何規則支援它時就能進遊戲驗證，
        // 比照 FabricDebugAnimationCommand 的 gating——正式產物裡根本沒註冊過。預設 inert。
        if (minecraftEnvironment.isDevelopment) {
            listOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.TAIWAN).forEach { ruleModuleId ->
                if (roundPreparationResolverRegistry.find(ruleModuleId) == null) {
                    roundPreparationResolverRegistry.register(DebugRoundPreparationResolver(ruleModuleId))
                }
            }
            winRoundContinuationResolverRegistry.registerDebugWinRoundContinuationResolvers(
                state = debugWinRoundContinuationState,
            )
        }

        MahjongExtensionRegistrar.registerAndFreeze(
            extensions = extensions,
            moduleRegistry = moduleRegistry,
            tileTypeRegistry = tileTypeRegistry,
            networkRegistries = networkRegistries,
            persistenceRegistries = persistenceRegistries,
            winCelebrationCueResolverRegistry = winCelebrationCueResolverRegistry,
            gameActionAiRegistry = gameActionAiRegistry,
            gameCommandRegistry = gameCommandRegistry,
            postReactionRoundOutcomeResolverRegistry = postReactionRoundOutcomeResolverRegistry,
            roundPreparationResolverRegistry = roundPreparationResolverRegistry,
            winRoundContinuationResolverRegistry = winRoundContinuationResolverRegistry,
            winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
        )

        // 同一個第三方類別可同時實作 MahjongExtension 與 MinecraftMahjongExtension，
        // 不需要在 fabric.mod.json 額外宣告第二個 entrypoint。
        return MinecraftMahjongExtensionRegistrar.registerAndFreeze(
            extensions = extensions.filterIsInstance<MinecraftMahjongExtension>(),
            tileAssetRegistry = minecraftTileAssetRegistry,
            aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
            tileDisplayNameRegistry = tileDisplayNameRegistry,
            ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            tileLabelRegistry = tileLabelRegistry,
            showcaseRegistry = showcaseRegistry,
            gameActionDisplayNameRegistry = gameActionDisplayNameRegistry,
            exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNameRegistry,
            roundPreparationDisplayNameRegistry = roundPreparationDisplayNameRegistry,
            winSettlementTemplateRegistry = winSettlementTemplateRegistry,
            matchSettlementTemplateRegistry = matchSettlementTemplateRegistry,
            playerPortraitSourceRegistry = playerPortraitSourceRegistry,
            publicPlayerIndicatorDisplayRegistry = publicPlayerIndicatorDisplayRegistry,
        )
    }

    /** [initialize] 預設使用的環境查詢：一律回報非開發環境，見該參數上方註解。 */
    private object NonDevelopmentEnvironment : MinecraftEnvironment {
        override val isDevelopment: Boolean = false
    }

    /** 將日麻限定的 action／command 整合集中安裝為 bundled Riichi extension。 */
    private fun registerBundledRiichiExtension(
        moduleRegistry: MahjongModuleRegistry,
        gameActionAiRegistry: ExtensionGameActionAiRegistry,
        gameCommandRegistry: ExtensionGameCommandExecutorRegistry,
        postReactionRoundOutcomeResolverRegistry: PostReactionRoundOutcomeResolverRegistry,
        gameActionDisplayNameRegistry: GameActionDisplayNameRegistry,
        declareRiichiUseCase: DeclareRiichiUseCase,
        networkRegistries: NetworkDtoRegistries,
        persistenceRegistries: PersistenceRegistries,
    ) {
        networkRegistries.registerRiichiGameActionDtos()
        persistenceRegistries.extensionGameActions.registerRiichiGameActionPersistenceDto()
        gameActionAiRegistry.registerRiichiGameActionHandler(moduleRegistry)
        gameCommandRegistry.registerRiichiGameCommandHandler(declareRiichiUseCase)
        postReactionRoundOutcomeResolverRegistry.registerRiichiNagashiManganOutcomeResolver()
        gameActionDisplayNameRegistry.registerRiichiGameActionDisplayName()
    }
}
