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
 * 在下一個客戶端 tick 開啟規則一覽，避免指令執行後關閉聊天畫面時蓋掉新畫面。
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

    /** 開啟一般說明的規則一覽，供指令與快捷鍵使用。 */
    fun openGeneral() = open()

    /**
     * 要求下一個 tick 開啟規則一覽。
     *
     * @param context 開啟時使用的唯讀規則脈絡。
     * @param parent 關閉後返回的畫面；沒有返回目標時為 null。
     */
    fun open(context: RuleCatalogueBrowseContext = RuleCatalogueBrowseContext(), parent: Screen? = null) {
        val client = MinecraftClient.getInstance()
        val connection = client.networkHandler ?: return
        val world = client.world ?: return
        pending = OpenRequest(connection, world, parent, context)
    }

    /**
     * 在下一個 client tick 驗證連線、世界與返回畫面仍然有效後建立畫面。
     *
     * @param client 當前 Minecraft client。
     */
    private fun tick(client: MinecraftClient) {
        val request = pending ?: return
        pending = null
        if (!request.isValid(client)) return
        val browser = RuleCatalogueBrowser(
            catalogues = catalogueRegistry,
            ruleNames = ruleNames,
            knownRuleIds = moduleRegistry.getAllModuleIds(),
            context = request.context,
        )
        val presenter = RuleCataloguePresenter(
            translate = ::currentLanguageTranslation,
            metrics = TextRendererCatalogueMetrics(client.textRenderer),
            tileArt = ResourceCatalogueTileArt(assets = tileAssets, resources = client.resourceManager),
        )
        client.setScreen(
            RuleCatalogueScreen(
                browser = browser,
                presenter = presenter,
                tileFaces = tileFaces,
                openingContext = request.context,
                parent = request.parent,
            ),
        )
    }
}

/**
 * 延後開啟規則一覽的連線與世界身分快照。
 * @property connection 要求時的連線。
 * @property world 要求時的世界。
 * @property parent 返回目標。
 * @property context 目錄的設定脈絡。
 */
private data class OpenRequest(
    val connection: Any,
    val world: Any,
    val parent: Screen?,
    val context: RuleCatalogueBrowseContext,
) {
    /**
     * 驗證開啟要求仍屬於目前連線、世界及返回畫面。
     * @param client 當前客戶端。
     * @return 是否可安全開啟。
     */
    fun isValid(client: MinecraftClient): Boolean = client.networkHandler === connection &&
        client.world === world &&
        (parent == null || client.currentScreen === parent)
}
