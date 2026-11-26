package com.doublemoon1119.mahjongcraft.platform.neoforge

import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationReport
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationReportFormatter
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.bootstrap.MahjongCraftClientBootstrap
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character.ClientAiCharacterPreviewRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tableprop.ClientTablePropPreviewRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.BuiltInNativeMahjongClientExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.NativeMahjongClientExtensionBootstrap
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientModels
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModPackets
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeBakedModelLookup
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeClientEvents
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeClientPacketTransport
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeClientRegistrar
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeExtensionDiscovery
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModList
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import org.koin.core.context.GlobalContext

/**
 * NeoForge 用戶端的初始化。
 *
 * - 共用的用戶端啟動流程要讀取已登記的物品與實體，NeoForge 在 mod 建構完成後才寫入登記表。
 * - 因此在第一個用戶端登記事件發生時才啟動：這時登記表已完成，按鍵、實體繪製、物品顏色、物品繪製與重載監聽都還沒登記。
 */
internal object MahjongCraftNeoForgeClient {
    /** 用戶端初始化的紀錄。 */
    private val logger = mahjongCraftLogger(MahjongCraftNeoForgeClient::class)

    /** 共用的用戶端啟動流程是否已經執行。 */
    private var started = false

    /**
     * 安裝用戶端的收送方式與模型查詢，接上本 mod 的事件匯流排，並登記設定畫面。
     *
     * @param modBus 本 mod 的事件匯流排。
     */
    fun initialize(modBus: IEventBus) {
        ModPackets.installClient(NeoForgeClientPacketTransport)
        ClientModels.install(NeoForgeBakedModelLookup)
        val registrar = NeoForgeClientRegistrar().also { it.attach(modBus) }
        NeoForgeClientEvents.attach(modBus)
        registerConfigScreen()
        // 以最高優先序接收，啟動流程交出的登記才能在同一個事件中套用。
        listOf(
            RegisterClientExtensionsEvent::class.java,
            RegisterColorHandlersEvent.Item::class.java,
            RegisterClientReloadListenersEvent::class.java,
            EntityRenderersEvent.RegisterRenderers::class.java,
            RegisterKeyMappingsEvent::class.java,
        ).forEach { eventType -> startOn(modBus, eventType, registrar) }
    }

    /** 讓 NeoForge 的模組清單以設定按鈕開啟用戶端設定畫面。 */
    private fun registerConfigScreen() {
        ModList.get().getModContainerById(MinecraftModMetadata.MOD_ID).orElseThrow().registerExtensionPoint(
            IConfigScreenFactory::class.java,
            IConfigScreenFactory { _, parent -> GlobalContext.get().get<MahjongClientConfigScreenController>().screen(parent) },
        )
    }

    /**
     * 在某個用戶端登記事件最先執行共用的用戶端啟動流程，只執行一次。
     *
     * @param E 事件型別。
     * @param modBus 本 mod 的事件匯流排。
     * @param eventType 事件型別。
     * @param registrar 用戶端登記。
     */
    private fun <E : Event> startOn(modBus: IEventBus, eventType: Class<E>, registrar: NeoForgeClientRegistrar) {
        modBus.addListener(EventPriority.HIGHEST, false, eventType) {
            if (!started) {
                started = true
                start(registrar)
            }
        }
    }

    /**
     * 登記用戶端擴充後執行共用的用戶端啟動流程。
     *
     * @param registrar 用戶端登記。
     */
    private fun start(registrar: NeoForgeClientRegistrar) {
        val koin = GlobalContext.get()
        val previewSources = NativeMahjongClientExtensionBootstrap.registerAndFreeze(
            extensions = NeoForgeExtensionDiscovery.discoverClient(),
            registry = koin.get<ClientAiCharacterPreviewRegistry>(),
            tablePropRegistry = koin.get<ClientTablePropPreviewRegistry>(),
            builtIn = BuiltInNativeMahjongClientExtension(tileAssets = koin.get<MinecraftTileAssetRegistry>()),
            failurePolicy = koin.get(),
        )
        val previewReport = ExtensionRegistrationReport(previewSources)
        logger.info(ExtensionRegistrationReportFormatter.formatSummary(previewReport))
        if (logger.isDebugEnabled) logger.debug(ExtensionRegistrationReportFormatter.formatDetails(previewReport))
        MahjongCraftClientBootstrap(koin = koin, events = NeoForgeClientEvents, registrar = registrar).initialize()
    }
}
