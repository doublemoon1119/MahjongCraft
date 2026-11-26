package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.di

import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.NativeExtensionRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.testLoaderModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.metadata.RuntimeMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.player.PlayerConnectionLifecycleService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.AiSeatPlacementService
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test

/** 驗證與載入器無關的 Koin 模組只需載入器補上少數定義。 */
class LoaderNeutralModuleTest {
    /** 每個測試結束後清除全域 Koin 容器。 */
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    /**
     * 與載入器無關的 server 與 client 模組，只要再提供載入器資訊、擴充失敗判斷、方塊使用攔截與光影陰影判斷就能組出完整服務。
     */
    @Test
    fun `loader neutral graphs only need loader bindings`() {
        val koin = startKoin {
            allowOverride(false)
            modules(modServerModule, modClientModule, testLoaderModule)
        }.koin

        koin.get<GameFlowCoordinator>()
        koin.get<MahjongTableRoomService>()
        koin.get<PlayerConnectionLifecycleService>()
        koin.get<AiSeatPlacementService>()
        koin.get<CoreExtensionRegistries>()
        koin.get<MinecraftPresentationRegistries>()
        koin.get<RuntimeMetadata>()
        koin.get<MinecraftEnvironment>()
        koin.get<ClientMahjongStateStore>()
        koin.get<MahjongClientConfigStore>()
        koin.get<NativeExtensionRegistries>()
    }
}
