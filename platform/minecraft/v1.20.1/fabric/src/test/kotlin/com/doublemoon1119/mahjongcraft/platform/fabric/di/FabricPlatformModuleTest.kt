package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.di.FlowServerModule
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateRestorer
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.concurrency.FabricAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.fabric.network.GameSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.network.RoomSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.room.MahjongTableRoomService
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.startKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * Smoke test：確認 [MahjongCraftApp]（也就是 [com.doublemoon1119.mahjongcraft.platform.fabric.MahjongCraftMod]
 * 實際啟動 Koin 時用的那個 `@KoinApplication` 組合——[FlowServerModule]，連同它 `includes` 進來的
 * `com.doublemoon1119.mahjongcraft.flow.common.di.FlowCommonModule`，再加上 [FabricPlatformModule]）
 * 解得出對局流程真正會用到的三個型別，不缺任何綁定。
 */
class FabricPlatformModuleTest {
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `Koin graph resolves gameplay publishers and table room lifecycle services`() {
        val koin = startKoin<MahjongCraftApp>().koin
        val moduleRegistry = koin.get<MahjongModuleRegistry>()
        val networkRegistries = koin.get<NetworkDtoRegistries>()
        val persistenceRegistries = koin.get<PersistenceRegistries>()
        FabricMahjongExtensions.initialize(moduleRegistry, networkRegistries, persistenceRegistries, emptyList())

        assertSame(moduleRegistry, koin.get<MahjongModuleRegistry>())
        assertSame(networkRegistries, koin.get<NetworkDtoRegistries>())
        assertSame(persistenceRegistries, koin.get<PersistenceRegistries>())
        koin.get<GameFlowCoordinator>()
        koin.get<GameEventPublisher>()
        koin.get<RoomEventPublisher>()
        koin.get<MahjongTableRoomService>()
        koin.get<RoomSnapshotSender>()
        koin.get<GameSnapshotSender>()
        koin.get<ServerSessionStateCleaner>()
        koin.get<ServerSessionStateRestorer>()
        koin.get<AuthoritativeStatePersistenceCodec>()
        koin.get<FabricAuthoritativeStatePersistence>()
        koin.get<FabricAppCoroutineScope>()
    }
}
