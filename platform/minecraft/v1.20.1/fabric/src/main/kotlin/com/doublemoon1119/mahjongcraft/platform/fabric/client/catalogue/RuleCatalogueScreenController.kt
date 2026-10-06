package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowseContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowser
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * 開啟規則一覽：一般說明延後到下一個客戶端 tick 開啟，避免指令執行後關閉聊天畫面時蓋掉新畫面；房間與歷史畫面立即切換。
 * @property catalogueRegistry 規則目錄來源。
 * @property ruleNames 規則名稱來源。
 * @property moduleRegistry 已知規則模組。
 * @property tileAssets 範例牌面素材映射。
 * @property tileFaces 現有牌面繪製器。
 */
@Single
class RuleCatalogueScreenController internal constructor(
    @Provided private val catalogueRegistry: RuleCatalogueRegistry,
    @Provided private val ruleNames: RuleModuleDisplayNameRegistry,
    @Provided private val moduleRegistry: MahjongModuleRegistry,
    @Provided private val tileAssets: MinecraftTileAssetRegistry,
    private val tileFaces: MahjongTileFaceRenderer,
) {
    /** 尚未執行的畫面開啟要求。 */
    private var pending: OpenRequest? = null

    /** 避免重複註冊客戶端 tick 事件。 */
    private var registered = false

    /** 註冊延後開啟事件；只能由 Fabric client entrypoint 呼叫一次。 */
    fun register() {
        if (registered) return
        registered = true
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    /** 在下一個 client tick 開啟一般說明的規則一覽，供指令與快捷鍵使用。 */
    fun openGeneral() {
        val client = MinecraftClient.getInstance()
        val connection = client.networkHandler ?: return
        val world = client.world ?: return
        pending = OpenRequest(connection, world)
    }

    /**
     * 建立規則一覽畫面，供房間或歷史畫面立即切換。
     *
     * @param context 開啟時的規則與設定來源。
     * @param exit 關閉後回到哪裡，以及開啟來源是否仍然有效。
     * @return 規則一覽畫面。
     */
    internal fun createScreen(context: RuleCatalogueBrowseContext, exit: RuleCatalogueExit): Screen {
        val client = MinecraftClient.getInstance()
        return RuleCatalogueScreen(
            browser = RuleCatalogueBrowser(
                catalogues = catalogueRegistry,
                ruleNames = ruleNames,
                knownRuleIds = moduleRegistry.getAllModuleIds(),
                context = context,
            ),
            presenter = RuleCataloguePresenter(
                translate = ::currentLanguageTranslation,
                metrics = TextRendererCatalogueMetrics(client.textRenderer),
                tileArt = ResourceCatalogueTileArt(assets = tileAssets, resources = client.resourceManager),
            ),
            tileFaces = tileFaces,
            openingContext = context,
            exit = exit,
        )
    }

    /**
     * 在下一個 client tick 確認連線與世界仍然相同後開啟一般說明。
     *
     * @param client 當前 Minecraft client。
     */
    private fun tick(client: MinecraftClient) {
        val request = pending ?: return
        pending = null
        if (!request.isValid(client)) return
        client.setScreen(createScreen(context = RuleCatalogueBrowseContext(), exit = CloseRuleCatalogueToGame))
    }
}

/**
 * 延後開啟一般說明時的連線與世界快照。
 *
 * @property connection 要求時的連線。
 * @property world 要求時的世界。
 */
private data class OpenRequest(
    val connection: Any,
    val world: Any,
) {
    /**
     * 確認要求仍屬於目前的連線與世界。
     *
     * @param client 當前客戶端。
     * @return 是否可安全開啟。
     */
    fun isValid(client: MinecraftClient): Boolean = client.networkHandler === connection && client.world === world
}
