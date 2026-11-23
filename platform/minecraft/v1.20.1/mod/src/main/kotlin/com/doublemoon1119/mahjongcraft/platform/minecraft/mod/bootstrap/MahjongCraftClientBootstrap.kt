package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.bootstrap

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDomain
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterClientStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSyncPayload
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.toCharacterSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.toSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.toDomain
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.ClientSessionLifecycleOwner
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.AutomaticControlCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutoSortHandPreferenceService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutomaticControlUpdateCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.catalogue.RuleCatalogueScreenCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.catalogue.RuleCatalogueScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character.ClientAiCharacterPreviews
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character.ClientAiSeatInteraction
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.ClientConfigCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.ClientDecisionPromptStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.PlayerDecisionHudController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.buildMatchResultChatMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.buildRoundResultChatMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideScreenCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.handbook.HandbookScreenCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.handbook.MahjongHandbookScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.ClientHistoryArchiveStatusCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.ClientHistoryChatEntryStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.ClientHistoryQueryCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.ClientHistoryQuerySettings
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryScreenCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.model.MahjongTileModelLoadingPlugin
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.ClientPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.ClientPlayerProfileResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.ClientTileAppearanceResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.DiceRollPresentationEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.ExhaustiveDrawSettlementPresentationEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongBotEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongDiceEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongLobbyInfoEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongPlayerInfoEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongRoundInfoEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongRulerEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongRulerTint
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongScoringStickEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongSoundTimelineEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongStoolSeatEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongTileEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongTileItemRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongTileSelectionConfirmEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MatchSettlementPresentationEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.PlayerPortraitRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.PublicPlayerIndicatorTextResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.SceneEntityTranslucency
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.TablePropTileEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.WinCelebrationEffectEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.WinCelebrationShowcaseEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.WinSettlementPresentationEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.WorldPanelDeferral
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.RoomConfigScreenCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.RoomScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.isShowingRoom
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.isShowingRoomForTable
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.table.ClientTableSpaceObstructionStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.table.TableSpaceObstructionRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tableprop.ClientTablePropPreviews
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tile.ClientDiscardSettlementTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tile.ClientHandTileLayoutBridge
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tile.ClientHandTileLayoutController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tile.HandSortCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tile.LatestDiscardHighlightController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tile.MatchingTileHighlightController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tile.TileLabelCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.time.PauseAwareClientMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item.MahjongScoringStickItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item.toModelPredicateValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerIdentityPayload
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.koin.core.Koin
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 與載入器無關的 client 初始化流程：註冊 client-only 命令、網路接收器、繪製器與客戶端事件。
 *
 * 載入器進入點先安裝自己的收送方式與模型查詢，並完成預覽擴充登記，再呼叫 [initialize]。
 *
 * @property koin 目前執行環境的依賴容器。
 * @property events 載入器提供的客戶端事件。
 * @property registrar 載入器提供的 client 內容註冊方式。
 */
class MahjongCraftClientBootstrap(
    private val koin: Koin,
    private val events: ClientEvents,
    private val registrar: ClientRegistrar,
) {
    /** Client 初始化與網路處理的記錄器。 */
    private val logger = mahjongCraftLogger(MahjongCraftClientBootstrap::class)

    /** 註冊所有 client 內容並掛上客戶端事件。 */
    fun initialize() {
        SceneEntityTranslucency.registerShader(events)
        val characterPreviews = koin.get<ClientAiCharacterPreviews>()
        characterPreviews.initialize(events)
        koin.get<ClientTablePropPreviews>().initialize(events)
        val portraits = koin.get<PlayerPortraitRenderer>()
        events.onClientResourceReload(ResourceLocation("mahjongcraft", "character_portrait_resources")) {
            Minecraft.getInstance().execute { portraits.clearResources() }
        }
        ClientAiSeatInteraction.register(
            registry = koin.get(),
            failures = koin.get(),
            json = koin.get(),
            events = events,
        )

        registrar.registerModelLoading(MahjongTileModelLoadingPlugin)
        registrar.registerItemRenderer(ModItems.MAHJONG_TILE, MahjongTileItemRenderer)
        registrar.registerItemProperty(
            item = ModItems.MAHJONG_SCORING_STICK,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "denomination"),
        ) { stack, _, _, _ -> MahjongScoringStickItem.readDenomination(stack).toModelPredicateValue() }
        ModItems.MAHJONG_RULERS.forEach { (color, item) ->
            registrar.registerItemColor(color = { _, _ -> MahjongRulerTint.rgb(color) }, items = listOf(item))
        }
        val clientConfigStore = koin.get<MahjongClientConfigStore>()
        koin.get<RoomConfigScreenCommand>().register(events)
        initializeClientConfig(clientConfigStore)
        koin.get<MahjongClientConfigScreenController>().register(events)
        koin.get<TileLabelCommand>().register(events)
        koin.get<HandSortCommand>().register(events)
        koin.get<AutomaticControlCommand>().register(events)
        koin.get<ClientConfigCommand>().register(events)
        koin.get<HistoryScreenController>().register(events)
        koin.get<HistoryScreenCommand>().register(events)
        val ruleCatalogueController = koin.get<RuleCatalogueScreenController>().also { it.register(events) }
        koin.get<RuleCatalogueScreenCommand>().register(events)
        koin.get<MahjongHandbookScreenController>().register(events)
        koin.get<HandbookScreenCommand>().register(events)
        koin.get<GuideScreenController>().register(events)
        koin.get<GuideScreenCommand>().register(events)
        val decisionHudController = koin.get<PlayerDecisionHudController>()
        decisionHudController.registerEvents(events)
        koin.get<MatchingTileHighlightController>().register(events)
        koin.get<ClientDiscardSettlementTracker>().register(events)
        koin.get<LatestDiscardHighlightController>().register(events)

        val json = koin.get<NetworkJson>()
        val historyQueries = koin.get<ClientHistoryQueryCoordinator>()
        val historyQuerySettings = koin.get<ClientHistoryQuerySettings>()
        MahjongChannels.historyQuerySettings.registerClientReceiver(json, historyQuerySettings::apply)
        val historyArchiveStatus = koin.get<ClientHistoryArchiveStatusCoordinator>()
        val historyChatEntries = koin.get<ClientHistoryChatEntryStore>()
        MahjongChannels.historyListResponse.registerClientReceiver(json, historyQueries::applyList)
        MahjongChannels.historySummaryResponse.registerClientReceiver(json, historyQueries::applySummary)
        MahjongChannels.aiCharacterHistory.registerClientReceiver(json) { payload ->
            if (!historyQueries.acceptsCharacterAppearance(payload.requestId, payload.matchId) || payload.players.size > AiCharacterSyncPayload.MAX_PLAYERS) return@registerClientReceiver
            try {
                val players = payload.players.map { Uuid.parse(it.playerId) to it.toCharacterSnapshot() }
                require(players.distinctBy { it.first }.size == players.size) { "Duplicate historical character player" }
                koin.get<AiCharacterClientStateStore>().rememberHistory(Uuid.parse(payload.matchId), players.toMap())
            } catch (cause: IllegalArgumentException) {
                logger.warn("Rejected invalid historical character appearance", cause)
            }
        }
        MahjongChannels.historyRuleSettingsResponse.registerClientReceiver(json, historyQueries::applyRuleSettings)
        MahjongChannels.historyRoundEventsResponse.registerClientReceiver(json, historyQueries::applyRoundEvents)
        MahjongChannels.historyRoundStateResponse.registerClientReceiver(json, historyQueries::applyRoundState)
        MahjongChannels.historyArchiveStatusResponse.registerClientReceiver(json, historyArchiveStatus::apply)
        val networkRegistries = koin.get<NetworkDtoRegistries>()
        val stateStore = koin.get<ClientMahjongStateStore>()
        val characterState = koin.get<AiCharacterClientStateStore>()
        val tableSpaceObstructions = koin.get<ClientTableSpaceObstructionStore>()
        koin.get<TableSpaceObstructionRenderer>().registerEvents(events)
        WorldPanelDeferral.register(events)
        val handTileLayoutController = koin.get<ClientHandTileLayoutController>()
        ClientHandTileLayoutBridge.activate(handTileLayoutController)
        val automaticControlUpdateCoordinator = koin.get<ClientAutomaticControlUpdateCoordinator>()
        val autoSortHandPreferenceService = koin.get<ClientAutoSortHandPreferenceService>()
        val decisionTimerStore = koin.get<ClientDecisionTimerStateStore>()
        val decisionPromptStore = koin.get<ClientDecisionPromptStore>()
        val tileDisplayNames = koin.get<TileDisplayNameRegistry>()
        val tileAssetRegistry = koin.get<MinecraftTileAssetRegistry>()
        val tileEmojiRegistry = koin.get<TileEmojiRegistry>()
        val tileFaceRenderer = koin.get<MahjongTileFaceRenderer>()
        val tileAppearanceResolver = koin.get<ClientTileAppearanceResolver>()
        val actionVocabulary = koin.get<GameActionVocabularyRegistry>()
        val moduleRegistry = koin.get<MahjongModuleRegistry>()
        val showcaseRegistry = koin.get<WinCelebrationShowcaseRegistry>()
        val exhaustiveDrawReasonDisplayNames = koin.get<ExhaustiveDrawReasonDisplayNameRegistry>()
        val roundOutcomeDisplayNames = koin.get<RoundOutcomeDisplayNameRegistry>()
        val roundInfoLineDisplayRegistry = koin.get<RoundInfoLineDisplayRegistry>()
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
        val playerNames = koin.get<ClientPlayerDisplayNameResolver>()
        val profileResolver = koin.get<ClientPlayerProfileResolver>()
        val sessionLifecycle = ClientSessionLifecycleOwner(
            clearHistoryQueries = historyQueries::clear,
            clearHistoryArchiveStatus = historyArchiveStatus::clear,
            clearProfiles = profileResolver::clearSession,
            clearHistoryChatEntries = historyChatEntries::clear,
            clearGameState = {
                stateStore.clear()
                characterState.clear()
                characterPreviews.clear()
                portraits.clearResources()
            },
            clearAutomaticControl = automaticControlUpdateCoordinator::clear,
            clearDecisionTimer = decisionTimerStore::clear,
            clearDecisionPrompt = decisionPromptStore::clear,
            clearDecisionHud = decisionHudController::clear,
            clearTileAppearance = tileAppearanceResolver::clear,
            clearHandTileLayout = ClientHandTileLayoutBridge::clear,
            clearTableSpaceObstruction = tableSpaceObstructions::clear,
        )
        MahjongChannels.aiCharacterSnapshot.registerClientReceiver(json) { payload ->
            try {
                characterState.receive(payload.toSnapshot())
            } catch (cause: IllegalArgumentException) {
                logger.warn("Rejected invalid public character snapshot", cause)
            }
        }
        // 伺服器送來格式錯誤的資料時只記錄第一次，之後同樣的錯誤不再記錄。
        var reportedInvalidIdentity = false
        MahjongChannels.playerIdentity.registerClientReceiver(json) { payload ->
            if (payload.entries.size > PlayerIdentityPayload.MAX_ENTRIES) return@registerClientReceiver
            payload.entries.forEach { entry ->
                val id = runCatching { Uuid.parse(entry.playerId) }.getOrNull()
                if (id == null) {
                    if (!reportedInvalidIdentity) logger.debug("Ignored a player name from the server with an invalid player ID: {}", entry.playerId)
                    reportedInvalidIdentity = true
                    return@forEach
                }
                profileResolver.rememberServerName(id, entry.name)
            }
        }
        MahjongChannels.roomUpdate.registerClientReceiver(json, stateStore::apply)
        MahjongChannels.gameUpdate.registerClientReceiver(json) { payload ->
            val gameId = Uuid.parse(payload.gameId)
            stateStore.apply(payload)
            val action = payload.action.toDomain(networkRegistries)
            val newSnapshot = stateStore.gameSnapshot(gameId) ?: return@registerClientReceiver
            // 這個封包也會送給旁觀者（供其畫面同步用），對局結算的聊天訊息只該發給實際入座的玩家。
            val localPlayerId = Minecraft.getInstance().player?.uuid?.toKotlinUuid()
            if (newSnapshot.players.none { it.id == localPlayerId }) return@registerClientReceiver
            val message = buildMatchResultChatMessage(
                action = action,
                newSnapshot = newSnapshot,
                aiPlayerIds = stateStore.gameAiPlayerIds(gameId),
                module = moduleRegistry.getModule(newSnapshot.config),
                playerDisplayName = { id, isAi -> playerNames.resolve(gameId, id.toString(), isAi) },
                historyCommand = if (action is GameAction.MatchEnded) historyChatEntries.createCommand(payload.historyMatchId) else null,
            )
                ?: return@registerClientReceiver
            Minecraft.getInstance().player?.sendSystemMessage(message)
        }
        MahjongChannels.roundResult.registerClientReceiver(json) { payload ->
            val message = buildRoundResultChatMessage(
                result = payload,
                actionVocabularyRegistry = actionVocabulary,
                roundOutcomeDisplayNameRegistry = roundOutcomeDisplayNames,
                exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNames,
                playerDisplayName = { id, isAi -> playerNames.resolve(Uuid.parse(payload.gameId), id.toString(), isAi) },
            )
            Minecraft.getInstance().player?.sendSystemMessage(message)
        }
        MahjongChannels.roomSnapshot.registerClientReceiver(json) { payload ->
            automaticControlUpdateCoordinator.clearTable(Uuid.parse(payload.roomId))
            stateStore.applyRoomSnapshot(
                roomId = Uuid.parse(payload.roomId),
                snapshot = payload.snapshot.toDomain(networkRegistries),
            )
        }
        MahjongChannels.tableOccupancy.registerClientReceiver(json) { payload ->
            stateStore.apply(payload)
            val client = Minecraft.getInstance()
            if (!isShowingRoom(client.screen)) {
                client.setScreen(
                    RoomScreen(
                        stateStore = stateStore,
                        characters = characterState,
                        displayNames = koin.get(),
                        clock = koin.get<PauseAwareClientMonotonicClock>(),
                        tableId = Uuid.parse(payload.tableId),
                        configPresentations = configPresentations,
                        configResolver = configResolver,
                        ruleNames = ruleNames,
                        portraitRenderer = portraitRenderer,
                        aiStrategies = aiStrategies,
                        aiStrategyNames = aiStrategyNames,
                        appearanceSources = appearanceSources,
                        indicatorTextResolver = indicatorTextResolver,
                        json = json,
                        networkRegistries = networkRegistries,
                        profileResolver = profileResolver,
                        ruleCatalogue = ruleCatalogueController,
                        tableSpaceObstructions = tableSpaceObstructions,
                    ),
                )
            }
        }
        MahjongChannels.snapshotCleared.registerClientReceiver(json) { payload ->
            characterState.remove(Uuid.parse(payload.id))
            val tableId = Uuid.parse(payload.id)
            stateStore.applySnapshotCleared(tableId)
            automaticControlUpdateCoordinator.clearTable(tableId)
        }
        MahjongChannels.tableSpaceObstruction.registerClientReceiver(json, tableSpaceObstructions::apply)
        MahjongChannels.tableRemoved.registerClientReceiver(json) { payload ->
            characterState.remove(Uuid.parse(payload.tableId))
            val tableId = Uuid.parse(payload.tableId)
            stateStore.removeTable(tableId)
            tableSpaceObstructions.clearTable(tableId)
            tileAppearanceResolver.clear(tableId)
            automaticControlUpdateCoordinator.clearTable(tableId)
            handTileLayoutController.removeTable(tableId)
            val client = Minecraft.getInstance()
            if (isShowingRoomForTable(client.screen, tableId)) {
                client.setScreen(null)
            }
        }
        MahjongChannels.privateHandOrder.registerClientReceiver(json, handTileLayoutController::receive)
        MahjongChannels.gameSnapshot.registerClientReceiver(json) { payload ->
            stateStore.applyGameSnapshot(
                gameId = Uuid.parse(payload.gameId),
                snapshot = payload.snapshot.toDomain(networkRegistries),
                aiPlayerIds = payload.aiPlayerIds.mapTo(mutableSetOf(), Uuid::parse),
                ruleDecision = payload.ruleDecision?.toDomain(),
                handReadinessAnalysis = payload.handReadinessAnalysis,
            )
        }
        MahjongChannels.automaticControlSnapshot.registerClientReceiver(json) { snapshot ->
            if (snapshot == null) {
                automaticControlUpdateCoordinator.clear()
            } else {
                automaticControlUpdateCoordinator.applySnapshot(snapshot)
            }
        }
        MahjongChannels.automaticControlUpdateResult.registerClientReceiver(
            json = json,
            handler = automaticControlUpdateCoordinator::applyResult,
        )
        koin.get<PauseAwareClientMonotonicClock>().registerEvents(events)
        events.onClientTickEnd { automaticControlUpdateCoordinator.checkTimeout() }
        registrar.registerEntityRenderer(ModEntities.mahjongBot, ::MahjongBotEntityRenderer)
        registrar.registerEntityRenderer(ModEntities.mahjongDice, ::MahjongDiceEntityRenderer)
        registrar.registerEntityRenderer(ModEntities.mahjongSoundTimeline, ::MahjongSoundTimelineEntityRenderer)
        registrar.registerEntityRenderer(ModEntities.mahjongStoolSeat, ::MahjongStoolSeatEntityRenderer)
        registrar.registerEntityRenderer(ModEntities.diceRollPresentation) { context -> DiceRollPresentationEntityRenderer(context, clientConfigStore) }
        registrar.registerEntityRenderer(ModEntities.mahjongScoringStick, ::MahjongScoringStickEntityRenderer)
        registrar.registerEntityRenderer(ModEntities.mahjongRuler, ::MahjongRulerEntityRenderer)
        registrar.registerEntityRenderer(ModEntities.mahjongTileSelectionConfirm, ::MahjongTileSelectionConfirmEntityRenderer)
        registrar.registerEntityRenderer(ModEntities.mahjongRoundInfo) { context ->
            MahjongRoundInfoEntityRenderer(
                context = context,
                configStore = clientConfigStore,
                roundInfoLineDisplayRegistry = roundInfoLineDisplayRegistry,
                tileDisplayNameRegistry = tileDisplayNames,
                tileAssetRegistry = tileAssetRegistry,
                tileEmojiRegistry = tileEmojiRegistry,
            )
        }
        registrar.registerEntityRenderer(ModEntities.mahjongPlayerInfo) { context ->
            MahjongPlayerInfoEntityRenderer(context, portraitRenderer, indicatorTextResolver, clientConfigStore, playerNames)
        }
        registrar.registerEntityRenderer(ModEntities.mahjongLobbyInfo) { context ->
            MahjongLobbyInfoEntityRenderer(context, ruleNames, clientConfigStore)
        }
        registrar.registerEntityRenderer(ModEntities.winCelebrationEffect, ::WinCelebrationEffectEntityRenderer)
        registrar.registerEntityRenderer(ModEntities.winCelebrationShowcase) { context ->
            WinCelebrationShowcaseEntityRenderer(context, showcaseRegistry, tileFaceRenderer, tileAppearanceResolver)
        }
        registrar.registerEntityRenderer(ModEntities.exhaustiveDrawSettlementPresentation) { context ->
            ExhaustiveDrawSettlementPresentationEntityRenderer(context, exhaustiveDrawReasonDisplayNames, portraitRenderer, tileFaceRenderer, playerNames, clientConfigStore, tileAppearanceResolver)
        }
        registrar.registerEntityRenderer(ModEntities.winSettlementPresentation) { context ->
            WinSettlementPresentationEntityRenderer(context, winSettlementTemplates, portraitRenderer, tileFaceRenderer, playerNames, clientConfigStore, indicatorTextResolver, tileAppearanceResolver, tileAssetRegistry)
        }
        registrar.registerEntityRenderer(ModEntities.matchSettlementPresentation) { context ->
            MatchSettlementPresentationEntityRenderer(context, matchSettlementTemplates, portraitRenderer, playerNames, clientConfigStore)
        }
        registrar.registerEntityRenderer(ModEntities.tablePropTile) { context ->
            TablePropTileEntityRenderer(context, tileFaceRenderer)
        }
        registrar.registerEntityRenderer(ModEntities.mahjongTile) { context ->
            MahjongTileEntityRenderer(
                context = context,
                stateStore = stateStore,
                tileAssetRegistry = tileAssetRegistry,
                tileFaceRenderer = tileFaceRenderer,
                tileAppearanceResolver = tileAppearanceResolver,
                moduleRegistry = moduleRegistry,
                decisionPromptStore = decisionPromptStore,
                configStore = clientConfigStore,
                handTileLayoutController = handTileLayoutController,
            )
        }
        MahjongChannels.decisionTimerUpdate.registerClientReceiver(json) { payload ->
            val gameId = Uuid.parse(payload.gameId)
            val status = payload.status
            if (status == null) {
                decisionTimerStore.stop(gameId)
                decisionPromptStore.stop(gameId)
            } else {
                decisionTimerStore.apply(
                    gameId = gameId,
                    phase = status.phase.toDomain(),
                    baseRemaining = status.baseRemaining,
                    reserveRemaining = status.reserveRemaining,
                )
                decisionPromptStore.apply(gameId, status.prompt)
            }
        }
        MahjongChannels.decisionSubmissionResult.registerClientReceiver(json, decisionHudController::handleSubmissionResult)
        events.onClientJoin {
            sessionLifecycle.beginSession()
            MahjongChannels.requestSnapshot.sendToServer(json, Unit)
            // 伺服器端的自動整理手牌偏好純記憶體、不撐過伺服器重啟（見 HandSortPreferenceStore KDoc），
            // 每次加入世界都重送一次 client 本地記得的偏好，確保重啟後不需要玩家手動再切一次。
            autoSortHandPreferenceService.restoreToServer()
        }
        events.onClientDisconnect {
            sessionLifecycle.endSession()
        }
    }

    /**
     * 載入 client TOML；失敗時 store 會保留程式預設值並記錄詳細錯誤，對稱 [MahjongCraftBootstrap] 的 `initializeServerConfig`。
     *
     * @param configStore client 設定讀取與更新 store。
     */
    private fun initializeClientConfig(configStore: MahjongClientConfigStore) {
        when (val result = configStore.load()) {
            is MahjongClientConfigUpdateResult.Success -> {
                // 使用 SLF4J 參數化訊息，避免對應 log level 關閉時先建立字串。
                if (result.createdDefaultFile) {
                    logger.info("Created and loaded default client config at {}", configStore.path)
                } else {
                    logger.debug("Loaded client config from {}", configStore.path)
                }
            }
            is MahjongClientConfigUpdateResult.Failure -> {
                logger.warn("Using built-in MahjongCraft client config defaults because loading failed: {}", result.message)
            }
        }
    }
}
