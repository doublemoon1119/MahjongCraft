package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationReport
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationReportFormatter
import com.doublemoon1119.mahjongcraft.platform.fabric.bootstrap.MahjongCraftClientBootstrap
import com.doublemoon1119.mahjongcraft.platform.fabric.client.character.ClientAiCharacterPreviewRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tableprop.ClientTablePropPreviewRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.BuiltInNativeMahjongClientExtension
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.NativeMahjongClientExtensionBootstrap
import com.doublemoon1119.mahjongcraft.platform.fabric.loader.FabricBakedModelLookup
import com.doublemoon1119.mahjongcraft.platform.fabric.loader.FabricClientEvents
import com.doublemoon1119.mahjongcraft.platform.fabric.loader.FabricClientPacketTransport
import com.doublemoon1119.mahjongcraft.platform.fabric.loader.FabricClientRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.loader.FabricExtensionDiscovery
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientModels
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModPackets
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import net.fabricmc.api.ClientModInitializer
import org.koin.core.context.GlobalContext

/** Fabric client 初始化入口；安裝 Fabric 實作、登記預覽擴充後交給 [MahjongCraftClientBootstrap]。 */
class MahjongCraftModClient : ClientModInitializer {
    /** Fabric client 初始化的記錄器。 */
    private val logger = mahjongCraftLogger(MahjongCraftModClient::class)

    override fun onInitializeClient() {
        ModPackets.installClient(FabricClientPacketTransport)
        ClientModels.install(FabricBakedModelLookup)
        val koin = GlobalContext.get()
        val previewSources = NativeMahjongClientExtensionBootstrap.registerAndFreeze(
            extensions = FabricExtensionDiscovery.discoverClient(),
            registry = koin.get<ClientAiCharacterPreviewRegistry>(),
            tablePropRegistry = koin.get<ClientTablePropPreviewRegistry>(),
            builtIn = BuiltInNativeMahjongClientExtension(tileAssets = koin.get<MinecraftTileAssetRegistry>()),
            failurePolicy = koin.get(),
        )
        val previewReport = ExtensionRegistrationReport(previewSources)
        logger.info(ExtensionRegistrationReportFormatter.formatSummary(previewReport))
        if (logger.isDebugEnabled) logger.debug(ExtensionRegistrationReportFormatter.formatDetails(previewReport))
        MahjongCraftClientBootstrap(koin = koin, events = FabricClientEvents, registrar = FabricClientRegistrar).initialize()
    }
}
