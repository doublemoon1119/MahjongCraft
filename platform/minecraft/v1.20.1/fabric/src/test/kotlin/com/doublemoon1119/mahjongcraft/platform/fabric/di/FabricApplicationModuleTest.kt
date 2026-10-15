package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyDefinition
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.expectation.NeutralOpponentModel
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelDefinition
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.ai.expectation.ReadingDepth
import com.doublemoon1119.mahjongcraft.ai.riichi.RiichiOpponentModel
import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DefaultGameConfigProvider
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceManager
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetPlayerDecisionOptionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateRestorer
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailabilityIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardReadinessAnalyzer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatProviderRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricExtensionRegistries
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.fabric.metadata.FabricRuntimeMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.MahjongTileCollisionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricDecisionTimerScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.GameActionCandidateResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.PlayerDecisionPromptFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.FabricDebugCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.animation.FabricDebugAnimationCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision.FabricDebugDecisionCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationMode
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.FabricDebugPresentationCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression.FabricDebugProgressionCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression.FabricDebugRoundCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.DebugScriptedAiStrategy
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.FabricDebugScenarioCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPlayerTableScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPreviewEntityLifecycle
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugTilePreviewSupport
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugVirtualTableLayoutFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.text.FabricDebugTextCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricTableLocationPersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.BuiltInDefaultGameConfigProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.BuiltInFabricSeatProviderIds
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolSeatProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLocationValidationService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.OrphanedTableCleanupService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropKinds
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.startKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證獨立伺服器與客戶端的 Koin 依賴完整、互相隔離，且擴充登記完成後凍結。 */
class FabricApplicationModuleTest {
    /** 每個測試結束後清除全域 Koin 容器，避免依賴登記互相影響。 */
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    /** 第三方策略與其 Minecraft 顯示名稱由同一個 extension 完成登記，並進入正式使用的 registry。 */
    @Test
    fun `third party ai strategy registers through fabric bootstrap`() {
        val koin = startKoin<MahjongCraftServerApp> { allowOverride(false) }.koin
        val strategyRegistry = koin.get<MahjongAiStrategyRegistry>()
        val displayNames = koin.get<AiStrategyDisplayNameRegistry>()
        val extension = object : MahjongExtension, MinecraftMahjongExtension {
            /** 測試擴充的識別碼。 */
            override val id: String = "example:ai_strategy"

            /**
             * 此測試不登記規則模組。
             *
             * @param registry 規則模組登記表。
             */
            override fun registerRuleModules(registry: MahjongModuleRegistry) = Unit

            /**
             * 此測試不登記網路 DTO。
             *
             * @param registries 網路 DTO 登記集合。
             */
            override fun registerNetworkDtos(registries: NetworkDtoRegistries) = Unit

            /**
             * 此測試不登記持久化 DTO。
             *
             * @param registries 持久化 DTO 登記集合。
             */
            override fun registerPersistenceDtos(registries: PersistenceRegistries) = Unit

            /**
             * 登記測試用 AI 策略。
             *
             * @param registry AI 策略登記表。
             */
            override fun registerAiStrategies(registry: MahjongAiStrategyRegistry) {
                registry.register(
                    definition = MahjongAiStrategyDefinition("example:custom_strategy") {
                        RandomAiStrategy(koin.get<ExtensionGameActionAiRegistry>())
                    },
                )
            }

            /**
             * 登記測試用 AI 策略顯示名稱。
             *
             * @param registry AI 策略顯示名稱登記表。
             */
            override fun registerAiStrategyDisplayNames(registry: AiStrategyDisplayNameRegistry) {
                registry.register(AiStrategyDisplayNameDefinition("example:custom_strategy", "example.ai.custom_strategy"))
            }
        }

        val result = FabricMahjongExtensions.initialize(
            coreRegistries = koin.get<CoreExtensionRegistries>(),
            presentationRegistries = koin.get<MinecraftPresentationRegistries>(),
            fabricRegistries = koin.get<FabricExtensionRegistries>(),
            mahjongStoolSeatProvider = koin.get<MahjongStoolSeatProvider>(),
            debugWinRoundContinuationState = koin.get<DebugWinRoundContinuationState>(),
            minecraftEnvironment = object : MinecraftEnvironment {
                /** 此案例只登記正式環境擴充。 */
                override val isDevelopment: Boolean = false
            },
            failurePolicy = FabricExtensionFailurePolicy(),
            extensions = listOf(extension),
        )

        assertTrue("example:custom_strategy" in strategyRegistry.getAllStrategyKeys())
        assertIs<RandomAiStrategy>(strategyRegistry.resolve("example:custom_strategy"))
        assertEquals("example.ai.custom_strategy", displayNames.find("example:custom_strategy"))
        val extensionSource = result.sources.single { it.extensionId == "example:ai_strategy" }
        assertTrue(extensionSource.categories.any { it.id == "mahjongcraft:ai_strategy" && "example:custom_strategy" in it.registrationIds })
        assertTrue(extensionSource.categories.any { it.id == "mahjongcraft:ai_strategy_display_name" && "example:custom_strategy" in it.registrationIds })
        assertFailsWith<IllegalStateException> {
            strategyRegistry.register(
                definition = MahjongAiStrategyDefinition("example:late") {
                    RandomAiStrategy(koin.get<ExtensionGameActionAiRegistry>())
                },
            )
        }
    }

    /** 開發環境在凍結前以獨立的 debug 來源登記 debug 情境使用的腳本 AI。 */
    @Test
    fun `development environment registers scripted debug ai strategies before freezing`() {
        val koin = startKoin<MahjongCraftServerApp> { allowOverride(false) }.koin

        val result = FabricMahjongExtensions.initialize(
            coreRegistries = koin.get<CoreExtensionRegistries>(),
            presentationRegistries = koin.get<MinecraftPresentationRegistries>(),
            fabricRegistries = koin.get<FabricExtensionRegistries>(),
            mahjongStoolSeatProvider = koin.get<MahjongStoolSeatProvider>(),
            debugWinRoundContinuationState = koin.get<DebugWinRoundContinuationState>(),
            minecraftEnvironment = object : MinecraftEnvironment {
                /** 此案例以開發環境啟動擴充。 */
                override val isDevelopment: Boolean = true
            },
            failurePolicy = FabricExtensionFailurePolicy(),
            extensions = emptyList(),
        )

        assertTrue(DebugScriptedAiStrategy.TSUMOGIRI_KEY in koin.get<MahjongAiStrategyRegistry>().getAllStrategyKeys())
        val debugSource = result.sources.single { it.extensionId == "mahjongcraft:debug" }
        assertTrue(debugSource.categories.any { it.id == "mahjongcraft:ai_strategy" && DebugScriptedAiStrategy.TSUMOGIRI_KEY in it.registrationIds })
        assertTrue(result.sources.first().categories.none { category -> DebugScriptedAiStrategy.TSUMOGIRI_KEY in category.registrationIds })
        val table = FakeTableStateFactory.create(
            players = List(4) { FakeMahjongPlayerFactory.create() },
            config = RiichiRuleConfig(),
        )
        val context = WinRoundContinuationContext(
            previousTableState = table,
            settledTableState = table,
            winnerPlayerIds = setOf(table.players.first().id),
            ronDiscarderId = null,
            winningTileId = Uuid.random(),
        )
        val resolverRegistry = koin.get<WinRoundContinuationResolverRegistry>()
        val ruleModule = koin.get<MahjongModuleRegistry>().getModule(table.config)
        assertEquals(WinRoundDirective.EndRound, resolverRegistry.resolve(context, ruleModule))
        koin.get<DebugWinRoundContinuationState>().setMode(table.id, DebugWinRoundContinuationMode.FULL)
        assertIs<WinRoundDirective.ContinueRound>(resolverRegistry.resolve(context, ruleModule))
    }

    /** 驗證伺服器服務、內建擴充與除錯指令可解析，且不包含客戶端狀態儲存。 */
    @Test
    fun `server graph resolves gameplay services without client stores`() {
        val koin = startKoin<MahjongCraftServerApp> { allowOverride(false) }.koin
        val moduleRegistry = koin.get<MahjongModuleRegistry>()
        val networkRegistries = koin.get<NetworkDtoRegistries>()
        val persistenceRegistries = koin.get<PersistenceRegistries>()
        val tileTypeRegistry = koin.get<TileTypeRegistry>()
        val minecraftTileAssetRegistry = koin.get<MinecraftTileAssetRegistry>()
        val aiStrategyDisplayNameRegistry = koin.get<AiStrategyDisplayNameRegistry>()
        val tileDisplayNameRegistry = koin.get<TileDisplayNameRegistry>()
        val ruleModuleDisplayNameRegistry = koin.get<RuleModuleDisplayNameRegistry>()
        val tileEmojiRegistry = koin.get<TileEmojiRegistry>()
        val tileLabelRegistry = koin.get<TileLabelRegistry>()
        val gameActionAiRegistry = koin.get<ExtensionGameActionAiRegistry>()
        val gameActionCommandFactoryRegistry = koin.get<ExtensionGameActionCommandFactoryRegistry>()
        val gameCommandRegistry = koin.get<ExtensionGameCommandExecutorRegistry>()
        val actionVocabularyRegistry = koin.get<GameActionVocabularyRegistry>()
        val automaticControlDisplayRegistry = koin.get<AutomaticControlDisplayRegistry>()
        val decisionStatusDisplayNameRegistry = koin.get<DecisionStatusDisplayNameRegistry>()
        val coreRegistries = koin.get<CoreExtensionRegistries>()
        val presentationRegistries = koin.get<MinecraftPresentationRegistries>()
        val aiStrategyRegistry = koin.get<MahjongAiStrategyRegistry>()
        assertSame(aiStrategyRegistry, coreRegistries.aiStrategyRegistry)
        assertTrue(aiStrategyRegistry.getAllStrategyKeys().isEmpty())
        val opponentModelRegistry = koin.get<OpponentModelRegistry>()
        assertSame(opponentModelRegistry, coreRegistries.opponentModelRegistry)
        assertTrue(opponentModelRegistry.registrationKeys.isEmpty())
        assertFalse(gameActionAiRegistry.isRegistered(RiichiGameAction.Riichi::class))
        assertFalse(gameActionCommandFactoryRegistry.isRegistered(RiichiGameAction.Riichi::class))
        assertFalse(gameCommandRegistry.isRegistered(RiichiGameCommand::class))
        val result = FabricMahjongExtensions.initialize(
            coreRegistries = coreRegistries,
            presentationRegistries = presentationRegistries,
            fabricRegistries = koin.get<FabricExtensionRegistries>(),
            mahjongStoolSeatProvider = koin.get<MahjongStoolSeatProvider>(),
            debugWinRoundContinuationState = koin.get<DebugWinRoundContinuationState>(),
            minecraftEnvironment = object : MinecraftEnvironment {
                /** 此案例驗證正式環境的完整組裝。 */
                override val isDevelopment: Boolean = false
            },
            failurePolicy = FabricExtensionFailurePolicy(),
            extensions = emptyList(),
        )

        val seatProviders = koin.get<FabricSeatProviderRegistry>()
        val fabricRegistries = koin.get<FabricExtensionRegistries>()
        assertSame(koin.get<FabricTablePropKindRegistry>(), fabricRegistries.tablePropKindRegistry)
        assertSame(seatProviders, fabricRegistries.seatProviderRegistry)
        assertSame(fabricRegistries, koin.get<FabricExtensionRegistries>())
        assertTrue(seatProviders.isFrozen)
        assertSame(
            expected = koin.get<MahjongStoolSeatProvider>(),
            actual = seatProviders.entries().single { it.id == BuiltInFabricSeatProviderIds.MAHJONG_STOOL }.provider,
        )
        assertFailsWith<IllegalStateException> {
            seatProviders.register(RegisteredSeatProvider("example:late", koin.get<MahjongStoolSeatProvider>()))
        }
        assertSame(moduleRegistry, koin.get<MahjongModuleRegistry>())
        assertSame(networkRegistries, koin.get<NetworkDtoRegistries>())
        assertSame(persistenceRegistries, koin.get<PersistenceRegistries>())
        assertSame(tileTypeRegistry, koin.get<TileTypeRegistry>())
        assertSame(minecraftTileAssetRegistry, koin.get<MinecraftTileAssetRegistry>())
        assertSame(aiStrategyDisplayNameRegistry, koin.get<AiStrategyDisplayNameRegistry>())
        assertSame(tileDisplayNameRegistry, koin.get<TileDisplayNameRegistry>())
        assertSame(ruleModuleDisplayNameRegistry, koin.get<RuleModuleDisplayNameRegistry>())
        assertSame(tileEmojiRegistry, koin.get<TileEmojiRegistry>())
        assertSame(tileLabelRegistry, koin.get<TileLabelRegistry>())
        assertSame(moduleRegistry, coreRegistries.moduleRegistry)
        assertSame(tileTypeRegistry, coreRegistries.tileTypeRegistry)
        assertSame(networkRegistries, coreRegistries.networkRegistries)
        assertSame(persistenceRegistries, coreRegistries.persistenceRegistries)
        assertSame(minecraftTileAssetRegistry, presentationRegistries.tileAssetRegistry)
        assertSame(tileDisplayNameRegistry, presentationRegistries.tileDisplayNameRegistry)
        assertSame(automaticControlDisplayRegistry, presentationRegistries.automaticControlDisplayRegistry)
        assertSame(koin.get<RuleCatalogueRegistry>(), presentationRegistries.ruleCatalogueRegistry)
        assertTrue(presentationRegistries.ruleCatalogueRegistry.isFrozen)
        assertSame(koin.get<GameAchievementResolverRegistry>(), presentationRegistries.gameAchievementResolverRegistry)
        assertTrue(presentationRegistries.gameAchievementResolverRegistry.isFrozen)
        assertEquals(RiichiTileTypes.ALL + TaiwanTileTypes.ALL, tileTypeRegistry.getAll().map { it.id })
        assertTrue(minecraftTileAssetRegistry.isFrozen)
        assertTrue(aiStrategyDisplayNameRegistry.isFrozen)
        assertTrue(tileDisplayNameRegistry.isFrozen)
        assertTrue(ruleModuleDisplayNameRegistry.isFrozen)
        assertTrue(tileEmojiRegistry.isFrozen)
        assertTrue(tileLabelRegistry.isFrozen)
        assertTrue(gameActionAiRegistry.isRegistered(RiichiGameAction.Riichi::class))
        assertTrue(gameActionAiRegistry.isRegistered(RiichiGameAction.PullNorth::class))
        assertEquals(setOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.RIICHI_THREE_PLAYER), opponentModelRegistry.registrationKeys)
        assertEquals(listOf(null, "mahjongcraft:riichi", "mahjongcraft:taiwan"), result.sources.map { it.extensionId })
        assertTrue(result.sources[0].categories.none { it.id == "mahjongcraft:rule_module" }, "Rule modules must come from their bundled extensions")
        assertTrue(result.sources[0].categories.any { it.id == "mahjongcraft:ai_strategy" })
        assertTrue(result.sources[0].categories.any { it.id == "mahjongcraft:table_prop_kind" })
        assertTrue(result.sources[1].categories.any { it.id == "mahjongcraft:rule_module" && BuiltInRuleModuleIds.RIICHI in it.registrationIds })
        assertTrue(result.sources[1].categories.any { it.id == "mahjongcraft:opponent_model" && BuiltInRuleModuleIds.RIICHI in it.registrationIds })
        assertTrue(result.sources[2].categories.any { it.id == "mahjongcraft:rule_module" && BuiltInRuleModuleIds.TAIWAN in it.registrationIds })
        assertEquals(
            listOf(BuiltInAiStrategyKeys.BEGINNER, BuiltInAiStrategyKeys.INTERMEDIATE, BuiltInAiStrategyKeys.ADVANCED, RandomAiStrategy.KEY),
            aiStrategyRegistry.getAllStrategyKeys().toList(),
        )
        assertFailsWith<IllegalStateException> {
            aiStrategyRegistry.register(MahjongAiStrategyDefinition("example:late") { aiStrategyRegistry.resolve(null) })
        }
        assertIs<RiichiOpponentModel>(opponentModelRegistry.create(moduleRegistry.getModule(RiichiRuleConfig()), ReadingDepth.BASIC))
        assertFailsWith<IllegalStateException> {
            opponentModelRegistry.register(OpponentModelDefinition("example:late") { _, depth -> NeutralOpponentModel(depth) })
        }
        assertTrue(gameActionCommandFactoryRegistry.isRegistered(RiichiGameAction.Riichi::class))
        assertTrue(gameCommandRegistry.isRegistered(RiichiGameCommand::class))
        assertEquals(
            MinecraftMessageKeys.GAME_ACTION_RIICHI,
            actionVocabularyRegistry.find(BuiltInRuleModuleIds.RIICHI, RiichiGameAction.Riichi.id)?.labelKey,
        )
        assertTrue(actionVocabularyRegistry.isFrozen)
        assertTrue(automaticControlDisplayRegistry.isFrozen)
        assertTrue(decisionStatusDisplayNameRegistry.find(BuiltInRuleModuleIds.RIICHI, WaitingTileAvailabilityIds.WIN_AVAILABLE) != null)
        assertTrue(
            decisionStatusDisplayNameRegistry.find(
                BuiltInRuleModuleIds.RIICHI,
                RiichiDiscardReadinessAnalyzer.StatusIds.DISCARD_FURITEN,
            ) != null,
        )
        assertTrue(decisionStatusDisplayNameRegistry.isFrozen)
        assertTrue(presentationRegistries.tablePropDescriberRegistry.find(BuiltInRuleModuleIds.RIICHI) != null)
        assertTrue(presentationRegistries.tablePropDescriberRegistry.isFrozen)
        val tablePropKindRegistry = koin.get<FabricTablePropKindRegistry>()
        assertTrue(tablePropKindRegistry.find(BuiltInTablePropKinds.SCORING_STICK) != null)
        assertTrue(tablePropKindRegistry.isFrozen)
        koin.get<TablePropPresenter>()
        koin.get<GameFlowCoordinator>()
        koin.get<GameEventPublisher>()
        koin.get<DecisionTimerUpdatePublisher>()
        koin.get<RoomEventPublisher>()
        assertTrue(koin.get<DefaultGameConfigProvider>() is BuiltInDefaultGameConfigProvider)
        koin.get<MahjongTableRoomService>()
        koin.get<ServerSessionStateCleaner>()
        koin.get<ServerSessionStateRestorer>()
        koin.get<AuthoritativeStatePersistenceCodec>()
        koin.get<FabricAuthoritativeStatePersistence>()
        koin.get<TableLocationRegistry>()
        koin.get<FabricTableLocationPersistence>()
        koin.get<OrphanedTableCleanupService>()
        koin.get<FabricTableLifecycleService>()
        koin.get<FabricTableLocationValidationService>()
        koin.get<FabricAppCoroutineScope>()
        koin.get<FabricDecisionTimerScheduler>()
        val debugRoot = koin.get<FabricDebugCommand>().build().build()
        assertEquals("mahjongcraft", debugRoot.name)
        assertEquals(setOf("debug"), debugRoot.children.map { it.name }.toSet())
        val debugNode = debugRoot.children.single()
        assertEquals(
            setOf(
                "scenario",
                "history",
                "stress",
                "win",
                "showcase",
                "dice",
                "deal",
                "draw",
                "discard",
                "exhaustive_draw_settlement",
                "hovered_text",
                "win_settlement",
                "match_settlement",
                "match_progression",
                "decision_hud",
                "meld",
                "continuing_win",
                "win_showcase_override",
                "preparation",
                "round",
            ),
            debugNode.children.map { it.name }.toSet(),
            "every family subcommand is mounted under the debug root, and nothing else is",
        )
        assertEquals(
            setOf("list", "load", "stress"),
            koin.get<FabricDebugScenarioCommand>().build().build().children.map { it.name }.toSet(),
        )
        assertEquals(
            setOf("next", "combo"),
            koin.get<FabricDebugRoundCommand>().buildRoundCommand().build().children.map { it.name }.toSet(),
        )
        val presentationCommand = koin.get<FabricDebugPresentationCommand>()
        assertEquals(
            setOf("melds", "tsumo", "ron", "multi_ron", "phase"),
            presentationCommand.buildShowcaseCommand().build().children.map { it.name }.toSet(),
        )
        assertEquals(
            setOf("normal", "melds", "kyuushu", "score", "abortive"),
            presentationCommand.buildExhaustiveDrawSettlementCommand().build().children.map { it.name }.toSet(),
        )
        assertEquals(
            setOf("tsumo", "ron", "yakuman", "pao", "nagashi"),
            presentationCommand.buildWinSettlementCommand().build().children.map { it.name }.toSet(),
        )
        assertEquals(
            setOf("winner_count"),
            presentationCommand.buildWinSettlementCommand().build().children
                .single { it.name == "ron" }.children.map { it.name }.toSet(),
            "ron keeps its optional winner count argument",
        )
        assertEquals(
            setOf("clear", "cue"),
            presentationCommand.buildWinShowcaseOverrideCommand().build().children.map { it.name }.toSet(),
        )
        koin.get<FabricDebugTextCommand>()
        val decisionCommand = koin.get<FabricDebugDecisionCommand>()
        val decisionHudChildren = decisionCommand.buildDecisionHudCommand().build()
            .children.map { it.name }.toSet()
        assertTrue(
            decisionHudChildren.containsAll(listOf("timer", "chi", "pon", "kan", "ron", "clear")),
            "decision_hud keeps its preview literals and clear",
        )
        assertEquals(
            setOf("start", "submit", "timeout", "cancel"),
            decisionCommand.buildPreparationCommand().build().children.map { it.name }.toSet(),
        )
        koin.get<FabricDebugProgressionCommand>()
        koin.get<FabricDebugAnimationCommand>()
        koin.get<DebugTilePreviewSupport>()
        koin.get<DebugVirtualTableLayoutFactory>()
        koin.get<DebugPreviewEntityLifecycle>()
        koin.get<DebugPlayerTableScope>()
        koin.get<GetPlayerDecisionOptionsUseCase>()
        koin.get<GameActionCandidateResolver>()
        koin.get<PlayerDecisionPromptFactory>()
        koin.get<MahjongTileCollisionService>()
        koin.get<MahjongDiceRollPresenter>()
        koin.get<MinecraftServerConfigState>()
        koin.get<FabricServerConfigManager>()
        koin.get<FabricServerConfigCommand>()
        assertNull(koin.getOrNull<ClientMahjongStateStore>())
        assertNull(koin.getOrNull<ClientDecisionTimerStateStore>())
    }

    /** 驗證 registry 集合只包裝既有 single，並保留 single 與 factory 的生命週期。 */
    @Test
    fun `server graph preserves registry identity and service lifetimes`() {
        val koin = startKoin<MahjongCraftServerApp> { allowOverride(false) }.koin
        val core = koin.get<CoreExtensionRegistries>()
        val presentation = koin.get<MinecraftPresentationRegistries>()

        assertSame(koin.get<MahjongModuleRegistry>(), core.moduleRegistry)
        assertSame(koin.get<TileTypeRegistry>(), core.tileTypeRegistry)
        assertSame(koin.get<NetworkDtoRegistries>(), core.networkRegistries)
        assertSame(koin.get<PersistenceRegistries>(), core.persistenceRegistries)
        assertSame(koin.get<HistoryReplayProjectionRegistry>(), core.historyReplayProjectionRegistry)
        assertSame(koin.get<WinCelebrationCueResolverRegistry>(), core.winCelebrationCueResolverRegistry)
        assertSame(koin.get<ExtensionGameActionAiRegistry>(), core.gameActionAiRegistry)
        assertSame(koin.get<MahjongAiStrategyRegistry>(), core.aiStrategyRegistry)
        assertSame(koin.get<OpponentModelRegistry>(), core.opponentModelRegistry)
        assertSame(koin.get<ExtensionGameActionCommandFactoryRegistry>(), core.gameActionCommandFactoryRegistry)
        assertSame(koin.get<ExtensionGameCommandExecutorRegistry>(), core.gameCommandRegistry)
        assertSame(koin.get<PostReactionRoundOutcomeResolverRegistry>(), core.postReactionRoundOutcomeResolverRegistry)
        assertSame(koin.get<PostActionExhaustiveDrawResolverRegistry>(), core.postActionExhaustiveDrawResolverRegistry)
        assertSame(koin.get<RoundPreparationResolverRegistry>(), core.roundPreparationResolverRegistry)
        assertSame(koin.get<WinRoundContinuationResolverRegistry>(), core.winRoundContinuationResolverRegistry)
        assertSame(koin.get<WinSettlementDetailResolverRegistry>(), core.winSettlementDetailResolverRegistry)

        assertSame(koin.get<MinecraftTileAssetRegistry>(), presentation.tileAssetRegistry)
        assertSame(koin.get<RuleCatalogueRegistry>(), presentation.ruleCatalogueRegistry)
        assertSame(koin.get<TileDisplayNameRegistry>(), presentation.tileDisplayNameRegistry)
        assertSame(koin.get<TileEmojiRegistry>(), presentation.tileEmojiRegistry)
        assertSame(koin.get<TileLabelRegistry>(), presentation.tileLabelRegistry)
        assertSame(koin.get<GameActionVocabularyRegistry>(), presentation.gameActionVocabularyRegistry)
        assertSame(koin.get<AutomaticControlDisplayRegistry>(), presentation.automaticControlDisplayRegistry)
        assertSame(koin.get<DecisionStatusDisplayNameRegistry>(), presentation.decisionStatusDisplayNameRegistry)
        assertSame(koin.get<GameActionSoundPresentationRegistry>(), presentation.gameActionSoundPresentationRegistry)
        assertSame(koin.get<ExhaustiveDrawReasonDisplayNameRegistry>(), presentation.exhaustiveDrawReasonDisplayNameRegistry)
        assertSame(koin.get<RoundOutcomeDisplayNameRegistry>(), presentation.roundOutcomeDisplayNameRegistry)
        assertSame(koin.get<HistoryDiscardMarkerDisplayRegistry>(), presentation.historyDiscardMarkerDisplayRegistry)
        assertSame(koin.get<RoundPreparationDisplayNameRegistry>(), presentation.roundPreparationDisplayNameRegistry)
        assertSame(koin.get<RoundInfoLineDisplayRegistry>(), presentation.roundInfoLineDisplayRegistry)
        assertSame(koin.get<TablePropDescriberRegistry>(), presentation.tablePropDescriberRegistry)
        assertSame(koin.get<WinCelebrationShowcaseRegistry>(), presentation.winCelebrationShowcaseRegistry)
        assertSame(koin.get<WinSettlementPresentationTemplateRegistry>(), presentation.winSettlementTemplateRegistry)
        assertSame(koin.get<MatchSettlementPresentationTemplateRegistry>(), presentation.matchSettlementTemplateRegistry)
        assertSame(koin.get<AiStrategyDisplayNameRegistry>(), presentation.aiStrategyDisplayNameRegistry)
        assertSame(koin.get<RuleModuleDisplayNameRegistry>(), presentation.ruleModuleDisplayNameRegistry)
        assertSame(koin.get<PlayerPortraitSourceRegistry>(), presentation.playerPortraitSourceRegistry)
        assertSame(koin.get<PublicPlayerIndicatorDisplayRegistry>(), presentation.publicPlayerIndicatorDisplayRegistry)
        assertSame(koin.get<RoomMemberAppearanceSourceRegistry>(), presentation.roomMemberAppearanceSourceRegistry)
        assertSame(koin.get<GameConfigPresentationRegistry>(), presentation.gameConfigPresentationRegistry)
        assertSame(koin.get<GameAchievementResolverRegistry>(), presentation.gameAchievementResolverRegistry)

        assertSame(koin.get<AiDecisionExecutor>(), koin.get<AiDecisionExecutor>())
        assertSame(koin.get<AutomatedAdvanceManager>(), koin.get<AutomatedAdvanceManager>())
        assertSame(koin.get<AuthoritativeStateStore>(), koin.get<AuthoritativeStateStore>())
        assertSame(koin.get<GameRepository>(), koin.get<GameRepository>())
        assertSame(koin.get<FabricAppCoroutineScope>(), koin.get<FabricAppCoroutineScope>())
        assertSame(koin.get<FabricRuntimeMetadata>(), koin.get<FabricRuntimeMetadata>())
        assertSame(koin.get<MinecraftEnvironment>(), koin.get<MinecraftEnvironment>())
        assertSame(koin.get<TableLocationRegistry>(), koin.get<TableLocationRegistry>())
        assertSame(koin.get<MinecraftServerConfigState>(), koin.get<MinecraftServerConfigState>())
        assertSame(koin.get<GameConfigPresentationResolver>(), koin.get<GameConfigPresentationResolver>())
        assertSame(koin.get<FabricServerHolder>(), koin.get<FabricServerHolder>())
        assertIs<AiStrategyDisplayNameRegistryImpl>(koin.get<AiStrategyDisplayNameRegistry>())
        assertIs<RuleModuleDisplayNameRegistryImpl>(koin.get<RuleModuleDisplayNameRegistry>())
        assertIs<TileDisplayNameRegistryImpl>(koin.get<TileDisplayNameRegistry>())
        assertTrue(koin.get<GameFlowCoordinator>() !== koin.get<GameFlowCoordinator>())
        assertTrue(koin.get<GetPlayerDecisionOptionsUseCase>() !== koin.get<GetPlayerDecisionOptionsUseCase>())
    }

    /** 驗證客戶端依賴同時提供整合式伺服器服務與客戶端狀態儲存。 */
    @Test
    fun `client graph resolves integrated server services and client stores`() {
        val koin = startKoin<MahjongCraftClientApp> { allowOverride(false) }.koin

        koin.get<GameFlowCoordinator>()
        koin.get<ClientMahjongStateStore>()
        koin.get<ClientDecisionTimerStateStore>()
        assertSame(koin.get<ClientDecisionTimerStateStore>(), koin.get<ClientDecisionTimerStateStore>())
        assertSame(koin.get<FabricRuntimeMetadata>(), koin.get<FabricRuntimeMetadata>())
        assertSame(koin.get<MinecraftEnvironment>(), koin.get<MinecraftEnvironment>())
        val presentation = koin.get<MinecraftPresentationRegistries>()
        assertSame(koin.get<AiStrategyDisplayNameRegistry>(), presentation.aiStrategyDisplayNameRegistry)
        assertSame(koin.get<RuleModuleDisplayNameRegistry>(), presentation.ruleModuleDisplayNameRegistry)
        assertSame(koin.get<TileDisplayNameRegistry>(), presentation.tileDisplayNameRegistry)
        assertIs<AiStrategyDisplayNameRegistryImpl>(presentation.aiStrategyDisplayNameRegistry)
        assertIs<RuleModuleDisplayNameRegistryImpl>(presentation.ruleModuleDisplayNameRegistry)
        assertIs<TileDisplayNameRegistryImpl>(presentation.tileDisplayNameRegistry)
    }
}
