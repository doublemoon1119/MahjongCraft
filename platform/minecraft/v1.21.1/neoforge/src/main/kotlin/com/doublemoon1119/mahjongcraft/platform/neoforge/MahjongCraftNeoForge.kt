package com.doublemoon1119.mahjongcraft.platform.neoforge

import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.bootstrap.MahjongCraftBootstrap
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.di.modClientModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.MahjongCraftExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.NativeExtensionRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModPackets
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.MahjongStoolSeatProvider
import com.doublemoon1119.mahjongcraft.platform.neoforge.development.MahjongCraftDevelopmentTools
import com.doublemoon1119.mahjongcraft.platform.neoforge.di.neoForgeServerModule
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeChannelPayloads
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeExtensionDiscovery
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeLoaderPlatform
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeModRegistrar
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgePacketTransport
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeServerEvents
import net.neoforged.fml.common.Mod
import org.koin.core.Koin
import org.koin.core.context.startKoin
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

/** NeoForge mod 的初始化入口。安裝 NeoForge 實作、組裝 runtime graph、登記擴充後交給 [MahjongCraftBootstrap]，用戶端再交給 [MahjongCraftNeoForgeClient]。 */
@Mod(MinecraftModMetadata.MOD_ID)
object MahjongCraftNeoForge {
    init {
        val registrar = NeoForgeModRegistrar().also { it.attach(MOD_BUS) }
        NeoForgeChannelPayloads.attach(MOD_BUS)
        // 頻道在建立時就會用到收送方式，必須在任何頻道建立前安裝。
        ModPackets.install(NeoForgePacketTransport)
        val koin = startDependencyInjection()
        // 開發工具的服務要在擴充登記之前載入，開發工具登記的擴充才能取用。
        val developmentTools = MahjongCraftDevelopmentTools.discover(koin.get<MinecraftEnvironment>())
        koin.loadModules(developmentTools.flatMap { it.koinModules() })
        // Koin single 建立後，必須先於 NetworkJson 與遊戲流程服務第一次被解析前完成。
        MahjongCraftExtensions.initializeAndReport(
            coreRegistries = koin.get<CoreExtensionRegistries>(),
            presentationRegistries = koin.get<MinecraftPresentationRegistries>(),
            nativeRegistries = koin.get<NativeExtensionRegistries>(),
            mahjongStoolSeatProvider = koin.get<MahjongStoolSeatProvider>(),
            failurePolicy = koin.get<ExtensionFailurePolicy>(),
            extensions = NeoForgeExtensionDiscovery.discover(),
        )
        MahjongCraftBootstrap(koin = koin, events = NeoForgeServerEvents, registrar = registrar).initialize(developmentTools)
        if (NeoForgeLoaderPlatform.isClient) MahjongCraftNeoForgeClient.initialize(MOD_BUS)
    }

    /**
     * 依目前 NeoForge 執行環境啟動 dedicated-server 或 client/integrated-server graph。
     *
     * - Minecraft client 可啟動 integrated server，因此 client graph 同時包含 [neoForgeServerModule] 與 [modClientModule]。
     * - 連線到 dedicated server 時 server-side single 維持延遲建立，不會建立第二個 Koin container。
     * - Dedicated server 只載入 [neoForgeServerModule]，不登記 client state。
     *
     * @return 對應執行環境的 Koin 依賴容器。
     */
    private fun startDependencyInjection(): Koin = if (NeoForgeLoaderPlatform.isClient) {
        startKoin { modules(neoForgeServerModule, modClientModule) }.koin
    } else {
        startKoin { modules(neoForgeServerModule) }.koin
    }
}
