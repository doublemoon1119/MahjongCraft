package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDomain
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.FabricClientConfigCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.buildDiceRolledChatMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.buildMatchResultChatMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.buildRoundResultChatMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.client.model.MahjongTileModelLoadingPlugin
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.ExhaustiveDrawSettlementPresentationEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongDiceEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongPlayerInfoEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongRoundInfoEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongScoringStickEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileItemRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MatchSettlementPresentationEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.PlayerPortraitRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.PublicPlayerIndicatorTextResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.WinCelebrationEffectEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.WinCelebrationShowcaseEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.WinSettlementPresentationEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.room.FabricOpenRoomConfigScreenCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.room.RoomScreen
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.FabricHandSortCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.FabricTileLabelCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongScoringStickItem
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.client.item.ModelPredicateProviderRegistry
import net.minecraft.util.Identifier
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

class MahjongCraftModClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    override fun onInitializeClient() {
        val koin = GlobalContext.get()

        ModelLoadingPlugin.register(MahjongTileModelLoadingPlugin)
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.MAHJONG_TILE, MahjongTileItemRenderer)
        ModelPredicateProviderRegistry.register(
            ModItems.MAHJONG_SCORING_STICK,
            Identifier(MinecraftModMetadata.MOD_ID, "denomination"),
        ) { stack, _, _, _ -> MahjongScoringStickItem.readDenomination(stack).normalizedPredicateValue }
        val clientConfigStore = koin.get<MahjongClientConfigStore>()
        koin.get<FabricOpenRoomConfigScreenCommand>().register()
        initializeClientConfig(clientConfigStore)
        koin.get<FabricTileLabelCommand>().register()
        koin.get<FabricHandSortCommand>().register()
        koin.get<FabricClientConfigCommand>().register()

        val json = koin.get<kotlinx.serialization.json.Json>()
        val networkRegistries = koin.get<NetworkDtoRegistries>()
        val stateStore = koin.get<ClientMahjongStateStore>()
        val decisionTimerStore = koin.get<ClientDecisionTimerStateStore>()
        val tileDisplayNames = koin.get<TileDisplayNameRegistry>()
        val tileAssetRegistry = koin.get<MinecraftTileAssetRegistry>()
        val tileEmojiRegistry = koin.get<TileEmojiRegistry>()
        val tileLabelRegistry = koin.get<TileLabelRegistry>()
        val gameActionDisplayNames = koin.get<GameActionDisplayNameRegistry>()
        val moduleRegistry = koin.get<MahjongModuleRegistry>()
        val showcaseRegistry = koin.get<WinCelebrationShowcaseRegistry>()
        val exhaustiveDrawReasonDisplayNames = koin.get<ExhaustiveDrawReasonDisplayNameRegistry>()
        val winSettlementTemplates = koin.get<WinSettlementPresentationTemplateRegistry>()
        val matchSettlementTemplates = koin.get<MatchSettlementPresentationTemplateRegistry>()
        val portraitRenderer = koin.get<PlayerPortraitRenderer>()
        val indicatorTextResolver = koin.get<PublicPlayerIndicatorTextResolver>()
        val configPresentations = koin.get<GameConfigPresentationRegistry>()
        val configResolver = koin.get<GameConfigPresentationResolver>()
        val ruleNames = koin.get<RuleModuleDisplayNameRegistry>()
        val aiStrategies = koin.get<MahjongAiStrategyRegistry>()
        val aiStrategyNames = koin.get<AiStrategyDisplayNameRegistry>()
        val appearanceSources = koin.get<RoomMemberAppearanceSourceRegistry>()
        MahjongChannels.roomUpdate.registerClientReceiver(json, stateStore::apply)
        MahjongChannels.gameUpdate.registerClientReceiver(json) { payload ->
            val previousSnapshot = stateStore.gameSnapshot
            stateStore.apply(payload)
            val action = payload.action.toDomain(networkRegistries)
            val newSnapshot = stateStore.gameSnapshot ?: return@registerClientReceiver
            val module = moduleRegistry.getModule(newSnapshot.config)
            val message = buildRoundResultChatMessage(
                action = action,
                previousSnapshot = previousSnapshot,
                newSnapshot = newSnapshot,
                module = module,
                actionDisplayNameRegistry = gameActionDisplayNames,
                displayNameRegistry = tileDisplayNames,
                tileAssetRegistry = tileAssetRegistry,
                tileEmojiRegistry = tileEmojiRegistry,
                exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNames,
            ) ?: buildMatchResultChatMessage(action, newSnapshot, module) ?: buildDiceRolledChatMessage(action) ?: return@registerClientReceiver
            MinecraftClient.getInstance().player?.sendMessage(message)
        }
        MahjongChannels.roomSnapshot.registerClientReceiver(json) { payload ->
            stateStore.applyRoomSnapshot(
                Uuid.parse(payload.roomId),
                payload.snapshot.toDomain(networkRegistries),
            )
        }
        MahjongChannels.tableLobby.registerClientReceiver(json) { payload ->
            stateStore.apply(payload)
            val client = MinecraftClient.getInstance()
            if (client.currentScreen !is RoomScreen) {
                client.setScreen(
                    RoomScreen(
                        stateStore,
                        configPresentations,
                        configResolver,
                        ruleNames,
                        portraitRenderer,
                        aiStrategies,
                        aiStrategyNames,
                        appearanceSources,
                        indicatorTextResolver,
                        json,
                        networkRegistries,
                    ),
                )
            }
        }
        MahjongChannels.gameSnapshot.registerClientReceiver(json) { payload ->
            stateStore.applyGameSnapshot(
                Uuid.parse(payload.gameId),
                payload.snapshot.toDomain(networkRegistries),
                payload.roundPreparation?.toDomain(),
            )
        }
        EntityRendererRegistry.register(ModEntities.mahjongDice, ::MahjongDiceEntityRenderer)
        EntityRendererRegistry.register(ModEntities.mahjongScoringStick, ::MahjongScoringStickEntityRenderer)
        EntityRendererRegistry.register(ModEntities.mahjongRoundInfo, ::MahjongRoundInfoEntityRenderer)
        EntityRendererRegistry.register(ModEntities.mahjongPlayerInfo) { context ->
            MahjongPlayerInfoEntityRenderer(context, portraitRenderer, indicatorTextResolver)
        }
        EntityRendererRegistry.register(ModEntities.winCelebrationEffect, ::WinCelebrationEffectEntityRenderer)
        EntityRendererRegistry.register(ModEntities.winCelebrationShowcase) { context ->
            WinCelebrationShowcaseEntityRenderer(context, showcaseRegistry)
        }
        EntityRendererRegistry.register(ModEntities.exhaustiveDrawSettlementPresentation) { context ->
            ExhaustiveDrawSettlementPresentationEntityRenderer(context, exhaustiveDrawReasonDisplayNames, portraitRenderer)
        }
        EntityRendererRegistry.register(ModEntities.winSettlementPresentation) { context ->
            WinSettlementPresentationEntityRenderer(context, winSettlementTemplates, portraitRenderer)
        }
        EntityRendererRegistry.register(ModEntities.matchSettlementPresentation) { context ->
            MatchSettlementPresentationEntityRenderer(context, matchSettlementTemplates, portraitRenderer)
        }
        EntityRendererRegistry.register(ModEntities.mahjongTile) { context ->
            MahjongTileEntityRenderer(context, stateStore, tileAssetRegistry, tileLabelRegistry, clientConfigStore, moduleRegistry)
        }
        MahjongChannels.decisionTimerUpdate.registerClientReceiver(json) { payload ->
            val gameId = Uuid.parse(payload.gameId)
            val status = payload.status
            if (status == null) {
                decisionTimerStore.stop(gameId)
            } else {
                decisionTimerStore.apply(
                    gameId = gameId,
                    phase = status.phase.toDomain(),
                    baseRemainingMillis = status.baseRemainingMillis,
                    reserveRemainingMillis = status.reserveRemainingMillis,
                )
            }
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            MahjongChannels.requestSnapshot.sendToServer(json, Unit)
            // 伺服器端的自動整理手牌偏好純記憶體、不撐過伺服器重啟（見 HandSortPreferenceStore KDoc），
            // 每次加入世界都重送一次 client 本地記得的偏好，確保重啟後不需要玩家手動再切一次。
            MahjongChannels.setAutoSortHand.sendToServer(json, clientConfigStore.current.autoSortHandEnabled)
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            stateStore.clear()
            decisionTimerStore.clear()
        }
    }

    /** 載入 client TOML；失敗時 store 會保留程式預設值並記錄詳細錯誤，對稱 server 端 `initializeServerConfig`。 */
    private fun initializeClientConfig(configStore: MahjongClientConfigStore) {
        when (val result = configStore.load()) {
            is MahjongClientConfigUpdateResult.Success -> {
                // 使用 SLF4J 參數化訊息，避免對應 log level 關閉時先建立字串。
                if (result.createdDefaultFile) {
                    logger.info("Created and loaded default client config at {}", configStore.path)
                } else {
                    logger.info("Loaded client config from {}", configStore.path)
                }
            }
            is MahjongClientConfigUpdateResult.Failure -> {
                logger.warn("Using built-in MahjongCraft client config defaults because loading failed: {}", result.message)
            }
        }
    }
}
