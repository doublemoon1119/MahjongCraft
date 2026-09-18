package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DefaultGameConfigProvider
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.DeclareRiichiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetPlayerDecisionOptionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateRestorer
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricMahjongExtensions
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
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.FabricDebugPresentationCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression.FabricDebugProgressionCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression.FabricDebugRoundCommand
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
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLocationValidationService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.OrphanedTableCleanupService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropKinds
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.startKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Smoke test：確認 dedicated server 與 Minecraft client 使用的 Koin application 各自具有完整且隔離的
 * 依賴圖。
 */
class FabricApplicationModuleTest {
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `server graph resolves gameplay services without client stores`() {
        val koin = startKoin<MahjongCraftServerApp>().koin
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
        val gameActionDisplayNameRegistry = koin.get<GameActionDisplayNameRegistry>()
        val coreRegistries = koin.get<CoreExtensionRegistries>()
        val presentationRegistries = koin.get<MinecraftPresentationRegistries>()
        assertFalse(gameActionAiRegistry.isRegistered(RiichiGameAction.Riichi::class))
        assertFalse(gameActionCommandFactoryRegistry.isRegistered(RiichiGameAction.Riichi::class))
        assertFalse(gameCommandRegistry.isRegistered(RiichiGameCommand::class))
        FabricMahjongExtensions.initialize(
            coreRegistries = coreRegistries,
            presentationRegistries = presentationRegistries,
            tablePropKindRegistry = koin.get<FabricTablePropKindRegistry>(),
            declareRiichiUseCase = koin.get<DeclareRiichiUseCase>(),
            extensions = emptyList(),
        )

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
        assertEquals(RiichiTileTypes.ALL + TaiwanTileTypes.ALL, tileTypeRegistry.getAll().map { it.id })
        assertTrue(minecraftTileAssetRegistry.isFrozen)
        assertTrue(aiStrategyDisplayNameRegistry.isFrozen)
        assertTrue(tileDisplayNameRegistry.isFrozen)
        assertTrue(ruleModuleDisplayNameRegistry.isFrozen)
        assertTrue(tileEmojiRegistry.isFrozen)
        assertTrue(tileLabelRegistry.isFrozen)
        assertTrue(gameActionAiRegistry.isRegistered(RiichiGameAction.Riichi::class))
        assertTrue(gameActionCommandFactoryRegistry.isRegistered(RiichiGameAction.Riichi::class))
        assertTrue(gameCommandRegistry.isRegistered(RiichiGameCommand::class))
        assertEquals(MinecraftMessageKeys.GAME_ACTION_RIICHI, gameActionDisplayNameRegistry.find(RiichiGameAction.Riichi))
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
            setOf("tsumo", "ron", "multi_ron", "phase"),
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

    @Test
    fun `client graph resolves integrated server services and client stores`() {
        val koin = startKoin<MahjongCraftClientApp>().koin

        koin.get<GameFlowCoordinator>()
        koin.get<ClientMahjongStateStore>()
        koin.get<ClientDecisionTimerStateStore>()
    }
}
