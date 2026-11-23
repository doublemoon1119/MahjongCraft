package com.doublemoon1119.mahjongcraft.platform.forge

import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationReport
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationReportFormatter
import com.doublemoon1119.mahjongcraft.platform.forge.loader.ForgeBakedModelLookup
import com.doublemoon1119.mahjongcraft.platform.forge.loader.ForgeClientEvents
import com.doublemoon1119.mahjongcraft.platform.forge.loader.ForgeClientPacketTransport
import com.doublemoon1119.mahjongcraft.platform.forge.loader.ForgeClientRegistrar
import com.doublemoon1119.mahjongcraft.platform.forge.loader.ForgeExtensionDiscovery
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
import net.minecraftforge.client.ConfigScreenHandler
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent
import net.minecraftforge.client.event.RegisterColorHandlersEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.ModLoadingContext
import org.koin.core.context.GlobalContext

/**
 * Forge 用戶端的初始化；只在用戶端由 [MahjongCraftForge] 呼叫。
 *
 * 用戶端啟動流程會讀取已登記的物品與實體，Forge 卻在 mod 建構之後才寫入登記表，因此流程延到 Forge 第一個用戶端登記事件
 * 才執行：這時登記表已經寫好，而按鍵、實體繪製器、物品顏色與資源重載監聽都還沒登記。
 */
internal object MahjongCraftForgeClient {
    /** Forge 用戶端初始化的記錄器。 */
    private val logger = mahjongCraftLogger(MahjongCraftForgeClient::class)

    /** 用戶端啟動流程是否已執行。 */
    private var started = false

    /**
     * 安裝不需要讀取登記表的部分，並在第一個用戶端登記事件時執行其餘流程。
     *
     * @param modBus 本 mod 的事件匯流排。
     */
    fun initialize(modBus: IEventBus) {
        ModPackets.installClient(ForgeClientPacketTransport)
        ClientModels.install(ForgeBakedModelLookup)
        val registrar = ForgeClientRegistrar().also { it.attach(modBus) }
        ForgeClientEvents.attach(modBus)
        registerConfigScreen()
        // 以最高優先序接收，啟動流程交出的登記才能在同一個事件中套用。
        listOf(
            RegisterColorHandlersEvent.Item::class.java,
            RegisterClientReloadListenersEvent::class.java,
            EntityRenderersEvent.RegisterRenderers::class.java,
            RegisterKeyMappingsEvent::class.java,
        ).forEach { eventType -> startOn(modBus, eventType, registrar) }
    }

    /** 模組清單中本 mod 的「設定」按鈕開啟客戶端設定，返回時回到模組清單；設定畫面在按下時才取得。 */
    private fun registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory::class.java) {
            ConfigScreenHandler.ConfigScreenFactory { _, parent -> GlobalContext.get().get<MahjongClientConfigScreenController>().screen(parent) }
        }
    }

    /**
     * 在指定的登記事件發生時執行用戶端啟動流程；只執行一次。
     *
     * @param E 登記事件的型別。
     * @param modBus 本 mod 的事件匯流排。
     * @param eventType 登記事件的型別。
     * @param registrar 用戶端的登記方式。
     */
    private fun <E : Event> startOn(modBus: IEventBus, eventType: Class<E>, registrar: ForgeClientRegistrar) {
        modBus.addListener(EventPriority.HIGHEST, false, eventType) {
            if (!started) {
                started = true
                start(registrar)
            }
        }
    }

    /**
     * 登記客戶端擴充後交給 [MahjongCraftClientBootstrap]。
     *
     * @param registrar 用戶端的登記方式。
     */
    private fun start(registrar: ForgeClientRegistrar) {
        val koin = GlobalContext.get()
        val previewSources = NativeMahjongClientExtensionBootstrap.registerAndFreeze(
            extensions = ForgeExtensionDiscovery.discoverClient(),
            registry = koin.get<ClientAiCharacterPreviewRegistry>(),
            tablePropRegistry = koin.get<ClientTablePropPreviewRegistry>(),
            builtIn = BuiltInNativeMahjongClientExtension(tileAssets = koin.get<MinecraftTileAssetRegistry>()),
            failurePolicy = koin.get(),
        )
        val previewReport = ExtensionRegistrationReport(previewSources)
        logger.info(ExtensionRegistrationReportFormatter.formatSummary(previewReport))
        if (logger.isDebugEnabled) logger.debug(ExtensionRegistrationReportFormatter.formatDetails(previewReport))
        MahjongCraftClientBootstrap(koin = koin, events = ForgeClientEvents, registrar = registrar).initialize()
    }
}
