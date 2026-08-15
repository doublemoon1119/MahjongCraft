package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateRestorer
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
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
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.GameSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.RoomSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricTableLocationPersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLocationValidationService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.OrphanedTableCleanupService
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.startKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
        FabricMahjongExtensions.initialize(
            moduleRegistry,
            tileTypeRegistry,
            networkRegistries,
            persistenceRegistries,
            minecraftTileAssetRegistry,
            aiStrategyDisplayNameRegistry,
            tileDisplayNameRegistry,
            ruleModuleDisplayNameRegistry,
            tileEmojiRegistry,
            emptyList(),
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
        assertEquals(RiichiTileTypes.ALL + TaiwanTileTypes.ALL, tileTypeRegistry.getAll().map { it.id })
        assertTrue(minecraftTileAssetRegistry.isFrozen)
        assertTrue(aiStrategyDisplayNameRegistry.isFrozen)
        assertTrue(tileDisplayNameRegistry.isFrozen)
        assertTrue(ruleModuleDisplayNameRegistry.isFrozen)
        assertTrue(tileEmojiRegistry.isFrozen)
        koin.get<GameFlowCoordinator>()
        koin.get<GameEventPublisher>()
        koin.get<DecisionTimerUpdatePublisher>()
        koin.get<RoomEventPublisher>()
        koin.get<MahjongTableRoomService>()
        koin.get<RoomSnapshotSender>()
        koin.get<GameSnapshotSender>()
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
