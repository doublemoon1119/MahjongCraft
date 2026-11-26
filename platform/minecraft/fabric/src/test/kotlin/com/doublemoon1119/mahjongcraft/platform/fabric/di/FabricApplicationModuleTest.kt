package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyDefinition
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.api.registration.MahjongAiStrategyRegistrar
import com.doublemoon1119.mahjongcraft.ai.expectation.NeutralOpponentModel
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelDefinition
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.ai.expectation.ReadingDepth
import com.doublemoon1119.mahjongcraft.ai.riichi.RiichiOpponentModel
import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DefaultGameConfigProvider
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.network.dto.api.registration.NetworkDtoRegistrars
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration.PersistenceRegistrars
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceManager
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetPlayerDecisionOptionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateRestorer
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.api.registration.MahjongModuleRegistrar
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailabilityIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardReadinessAnalyzer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.AiStrategyDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.RuleDecisionDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryTileSetAsideDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.concurrency.ClientCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.concurrency.ClientThreadCoroutineDispatcher
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.di.modClientModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.LinkageIsolatingExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.MahjongCraftExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.NativeExtensionRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.IrisShadowPassProbe
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ShadowPassProbe
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.metadata.RuntimeMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.ServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.concurrency.ServerLifecycleAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config.ServerConfigCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config.ServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.entity.MahjongTileCollisionService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.DecisionTimerScheduler
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.GameActionCandidateResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.PlayerDecisionPromptFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.AuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.TableLocationPersistence
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room.BuiltInDefaultGameConfigProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.BuiltInSeatProviderIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.MahjongStoolSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.SeatProviderRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.OrphanedTableCleanupService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.TableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.TableLocationValidationService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.prop.TablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiTranslationKeys
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
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
        val koin = startKoin {
            allowOverride(false)
            modules(fabricServerModule)
        }.koin
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
            override fun registerRuleModules(registry: MahjongModuleRegistrar) = Unit

            /**
             * 此測試不登記網路 DTO。
             *
             * @param registries 網路 DTO 登記集合。
             */
            override fun registerNetworkDtos(registries: NetworkDtoRegistrars) = Unit

            /**
             * 此測試不登記持久化 DTO。
             *
             * @param registries 持久化 DTO 登記集合。
             */
            override fun registerPersistenceDtos(registries: PersistenceRegistrars) = Unit

            /**
             * 登記測試用 AI 策略。
             *
             * @param registry AI 策略登記表。
             */
            override fun registerAiStrategies(registry: MahjongAiStrategyRegistrar) {
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
            override fun registerAiStrategyDisplayNames(registry: AiStrategyDisplayNameRegistrar) {
                registry.register(AiStrategyDisplayNameDefinition("example:custom_strategy", "example.ai.custom_strategy"))
            }
        }

        val result = MahjongCraftExtensions.initialize(
            coreRegistries = koin.get<CoreExtensionRegistries>(),
            presentationRegistries = koin.get<MinecraftPresentationRegistries>(),
            nativeRegistries = koin.get<NativeExtensionRegistries>(),
            mahjongStoolSeatProvider = koin.get<MahjongStoolSeatProvider>(),
            failurePolicy = LinkageIsolatingExtensionFailurePolicy(),
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

    /** 驗證伺服器服務與內建擴充可解析，且不包含客戶端狀態儲存。 */
    @Test
    fun `server graph resolves gameplay services without client stores`() {
        val koin = startKoin {
            allowOverride(false)
            modules(fabricServerModule)
        }.koin
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
        val result = MahjongCraftExtensions.initialize(
            coreRegistries = coreRegistries,
            presentationRegistries = presentationRegistries,
            nativeRegistries = koin.get<NativeExtensionRegistries>(),
            mahjongStoolSeatProvider = koin.get<MahjongStoolSeatProvider>(),
            failurePolicy = LinkageIsolatingExtensionFailurePolicy(),
            extensions = emptyList(),
        )

        val seatProviders = koin.get<SeatProviderRegistryImpl>()
        val nativeRegistries = koin.get<NativeExtensionRegistries>()
        assertSame(koin.get<TablePropKindRegistry>(), nativeRegistries.tablePropKindRegistry)
        assertSame(seatProviders, nativeRegistries.seatProviderRegistry)
        assertSame(nativeRegistries, koin.get<NativeExtensionRegistries>())
        assertTrue(seatProviders.isFrozen)
        assertSame(
            expected = koin.get<MahjongStoolSeatProvider>(),
            actual = seatProviders.entries().single { it.id == BuiltInSeatProviderIds.MAHJONG_STOOL }.provider,
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
        assertEquals(TaiwanTileTypes.ALL, tileTypeRegistry.getAll().map { it.id })
        assertTrue(minecraftTileAssetRegistry.isFrozen)
        assertTrue(aiStrategyDisplayNameRegistry.isFrozen)
        assertTrue(tileDisplayNameRegistry.isFrozen)
        assertTrue(ruleModuleDisplayNameRegistry.isFrozen)
        assertTrue(tileEmojiRegistry.isFrozen)
        assertTrue(tileLabelRegistry.isFrozen)
        assertTrue(gameActionAiRegistry.isRegistered(RiichiGameAction.Riichi::class))
        assertTrue(gameActionAiRegistry.isRegistered(RiichiGameAction.PullNorth::class))
        assertEquals(setOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, BuiltInRuleModuleIds.TAIWAN), opponentModelRegistry.registrationKeys)
        assertEquals(listOf(null, "mahjongcraft:riichi", "mahjongcraft:taiwan"), result.sources.map { it.extensionId })
        assertTrue(result.sources[0].categories.none { it.id == "mahjongcraft:rule_module" }, "Rule modules must come from their bundled extensions")
        assertTrue(result.sources[0].categories.any { it.id == "mahjongcraft:ai_strategy" })
        assertTrue(result.sources[0].categories.any { it.id == "mahjongcraft:table_prop_kind" })
        assertTrue(result.sources[1].categories.any { it.id == "mahjongcraft:rule_module" && BuiltInRuleModuleIds.RIICHI in it.registrationIds })
        assertTrue(result.sources[1].categories.any { it.id == "mahjongcraft:opponent_model" && BuiltInRuleModuleIds.RIICHI in it.registrationIds })
        assertTrue(result.sources[2].categories.any { it.id == "mahjongcraft:rule_module" && BuiltInRuleModuleIds.TAIWAN in it.registrationIds })
        assertEquals(
            expected = listOf(BuiltInAiStrategyKeys.BEGINNER, BuiltInAiStrategyKeys.INTERMEDIATE, BuiltInAiStrategyKeys.ADVANCED, RandomAiStrategy.KEY),
            actual = aiStrategyRegistry.getAllStrategyKeys().toList(),
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
            expected = RiichiTranslationKeys.ACTION_RIICHI,
            actual = actionVocabularyRegistry.find(BuiltInRuleModuleIds.RIICHI, RiichiGameAction.Riichi.id)?.labelKey,
        )
        assertTrue(actionVocabularyRegistry.isFrozen)
        assertTrue(automaticControlDisplayRegistry.isFrozen)
        assertTrue(decisionStatusDisplayNameRegistry.find(BuiltInRuleModuleIds.RIICHI, WaitingTileAvailabilityIds.WIN_AVAILABLE) != null)
        assertTrue(
            actual = decisionStatusDisplayNameRegistry.find(
                ruleModuleId = BuiltInRuleModuleIds.RIICHI,
                statusId = RiichiDiscardReadinessAnalyzer.StatusIds.DISCARD_FURITEN,
            ) != null,
        )
        assertTrue(decisionStatusDisplayNameRegistry.isFrozen)
        assertTrue(presentationRegistries.tablePropDescriberRegistry.find(BuiltInRuleModuleIds.RIICHI) != null)
        assertTrue(presentationRegistries.tablePropDescriberRegistry.isFrozen)
        val tablePropKindRegistry = koin.get<TablePropKindRegistry>()
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
        koin.get<AuthoritativeStatePersistence>()
        koin.get<TableLocationRegistry>()
        koin.get<TableLocationPersistence>()
        koin.get<OrphanedTableCleanupService>()
        koin.get<TableLifecycleService>()
        koin.get<TableLocationValidationService>()
        koin.get<ServerLifecycleAppCoroutineScope>()
        koin.get<DecisionTimerScheduler>()
        koin.get<GetPlayerDecisionOptionsUseCase>()
        koin.get<GameActionCandidateResolver>()
        koin.get<PlayerDecisionPromptFactory>()
        koin.get<MahjongTileCollisionService>()
        koin.get<MahjongDiceRollPresenter>()
        koin.get<MinecraftServerConfigState>()
        koin.get<ServerConfigManager>()
        koin.get<ServerConfigCommand>()
        assertNull(koin.getOrNull<ClientMahjongStateStore>())
        assertNull(koin.getOrNull<ClientDecisionTimerStateStore>())
    }

    /** 驗證 registry 集合只包裝既有 single，並保留 single 與 factory 的生命週期。 */
    @Test
    fun `server graph preserves registry identity and service lifetimes`() {
        val koin = startKoin {
            allowOverride(false)
            modules(fabricServerModule)
        }.koin
        val core = koin.get<CoreExtensionRegistries>()
        val presentation = koin.get<MinecraftPresentationRegistries>()

        assertSame(koin.get<MahjongModuleRegistry>(), core.moduleRegistry)
        assertSame(koin.get<TileTypeRegistry>(), core.tileTypeRegistry)
        assertSame(koin.get<NetworkDtoRegistries>(), core.networkRegistries)
        assertSame(koin.get<PersistenceRegistries>(), core.persistenceRegistries)
        assertSame(koin.get<WinCelebrationCueResolverRegistry>(), core.winCelebrationCueResolverRegistry)
        assertSame(koin.get<ExtensionGameActionAiRegistry>(), core.gameActionAiRegistry)
        assertSame(koin.get<MahjongAiStrategyRegistry>(), core.aiStrategyRegistry)
        assertSame(koin.get<OpponentModelRegistry>(), core.opponentModelRegistry)
        assertSame(koin.get<ExtensionGameActionCommandFactoryRegistry>(), core.gameActionCommandFactoryRegistry)
        assertSame(koin.get<ExtensionGameCommandExecutorRegistry>(), core.gameCommandRegistry)
        assertSame(koin.get<PostReactionRoundOutcomeResolverRegistry>(), core.postReactionRoundOutcomeResolverRegistry)
        assertSame(koin.get<PostActionExhaustiveDrawResolverRegistry>(), core.postActionExhaustiveDrawResolverRegistry)
        assertSame(koin.get<RuleDecisionResolverRegistry>(), core.ruleDecisionResolverRegistry)
        assertSame(koin.get<WinRoundContinuationResolverRegistry>(), core.winRoundContinuationResolverRegistry)
        assertSame(koin.get<WinSettlementDetailResolverRegistry>(), core.winSettlementDetailResolverRegistry)
        assertSame(koin.get<GameEventPublicMarkerResolverRegistry>(), core.gameEventPublicMarkerResolverRegistry)

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
        assertSame(koin.get<HistoryTileSetAsideDisplayRegistry>(), presentation.historyTileSetAsideDisplayRegistry)
        assertSame(koin.get<RuleDecisionDisplayNameRegistry>(), presentation.ruleDecisionDisplayNameRegistry)
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
        assertSame(koin.get<ServerLifecycleAppCoroutineScope>(), koin.get<ServerLifecycleAppCoroutineScope>())
        assertSame(koin.get<RuntimeMetadata>(), koin.get<RuntimeMetadata>())
        assertSame(koin.get<MinecraftEnvironment>(), koin.get<MinecraftEnvironment>())
        assertSame(koin.get<TableLocationRegistry>(), koin.get<TableLocationRegistry>())
        assertSame(koin.get<MinecraftServerConfigState>(), koin.get<MinecraftServerConfigState>())
        assertSame(koin.get<GameConfigPresentationResolver>(), koin.get<GameConfigPresentationResolver>())
        assertSame(koin.get<ServerHolder>(), koin.get<ServerHolder>())
        assertIs<AiStrategyDisplayNameRegistryImpl>(koin.get<AiStrategyDisplayNameRegistry>())
        assertIs<RuleModuleDisplayNameRegistryImpl>(koin.get<RuleModuleDisplayNameRegistry>())
        assertIs<TileDisplayNameRegistryImpl>(koin.get<TileDisplayNameRegistry>())
        assertTrue(koin.get<GameFlowCoordinator>() !== koin.get<GameFlowCoordinator>())
        assertTrue(koin.get<GetPlayerDecisionOptionsUseCase>() !== koin.get<GetPlayerDecisionOptionsUseCase>())
    }

    /** 驗證客戶端依賴同時提供整合式伺服器服務與客戶端狀態儲存。 */
    @Test
    fun `client graph resolves integrated server services and client stores`() {
        val koin = startKoin {
            allowOverride(false)
            modules(fabricServerModule, modClientModule)
        }.koin

        koin.get<GameFlowCoordinator>()
        koin.get<ClientMahjongStateStore>()
        koin.get<ClientDecisionTimerStateStore>()
        assertIs<IrisShadowPassProbe>(koin.get<ShadowPassProbe>())
        assertSame(koin.get<ClientDecisionTimerStateStore>(), koin.get<ClientDecisionTimerStateStore>())
        assertSame(koin.get<RuntimeMetadata>(), koin.get<RuntimeMetadata>())
        assertSame(koin.get<MinecraftEnvironment>(), koin.get<MinecraftEnvironment>())
        val presentation = koin.get<MinecraftPresentationRegistries>()
        assertSame(koin.get<AiStrategyDisplayNameRegistry>(), presentation.aiStrategyDisplayNameRegistry)
        assertSame(koin.get<RuleModuleDisplayNameRegistry>(), presentation.ruleModuleDisplayNameRegistry)
        assertSame(koin.get<TileDisplayNameRegistry>(), presentation.tileDisplayNameRegistry)
        assertIs<AiStrategyDisplayNameRegistryImpl>(presentation.aiStrategyDisplayNameRegistry)
        assertIs<RuleModuleDisplayNameRegistryImpl>(presentation.ruleModuleDisplayNameRegistry)
        assertIs<TileDisplayNameRegistryImpl>(presentation.tileDisplayNameRegistry)
    }

    /** 只以介面取用的服務，在 client 依賴圖（含 integrated server）中解析到預期的實作，且與實作型別取得的是同一個 single。 */
    @Test
    fun `graphs bind services that consumers resolve by interface`() {
        val koin = startKoin {
            allowOverride(false)
            modules(fabricServerModule, modClientModule)
        }.koin

        assertSame(koin.get<BuiltInDefaultGameConfigProvider>(), koin.get<DefaultGameConfigProvider>())
        assertSame(koin.get<MahjongStoolSeatProvider>(), koin.get<SeatProvider<*, *>>())
        assertSame(koin.get<ClientCoroutineScope>(), koin.get<CoroutineScope>())
        assertSame(koin.get<ClientThreadCoroutineDispatcher>(), koin.get<CoroutineDispatcher>())
    }
}
