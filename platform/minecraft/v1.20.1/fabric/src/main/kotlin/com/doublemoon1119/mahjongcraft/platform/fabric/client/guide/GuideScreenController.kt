package com.doublemoon1119.mahjongcraft.platform.fabric.client.guide

import com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue.RuleCatalogueScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongHudPreviewRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.BuiltInRuleCatalogueCategoryIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.guide.MinecraftGuideScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil

/**
 * 開啟「如何開始」畫面：麻將手冊入口、按鍵（預設未綁定）與指令。
 *
 * 記住上次看的頁籤，再次開啟時從同一頁開始。
 *
 * @property tileFaces 平面牌面的繪製。
 * @property hudPreview 範例操作面板的繪製。
 * @property tileNames 牌名的查詢來源。
 * @property tileAssets 牌面素材鍵的查詢來源。
 * @property historyScreens 對局歷史畫面的開啟入口。
 * @property configScreens 客戶端設定畫面的開啟入口。
 * @property ruleCatalogueScreens 規則一覽畫面的開啟入口。
 */
class GuideScreenController internal constructor(
    private val tileFaces: MahjongTileFaceRenderer,
    private val hudPreview: MahjongHudPreviewRenderer,
    private val tileNames: TileDisplayNameRegistry,
    private val tileAssets: MinecraftTileAssetRegistry,
    private val historyScreens: HistoryScreenController,
    private val configScreens: MahjongClientConfigScreenController,
    private val ruleCatalogueScreens: RuleCatalogueScreenController,
) {
    /** 開啟畫面的按鍵，預設未綁定。 */
    private lateinit var openKeyBinding: KeyBinding

    /** 是否已完成事件註冊。 */
    private var registered = false

    /** 指令要求開啟、等待下一個 client tick 開啟。 */
    private var openRequested = false

    /** 上次看的頁籤。 */
    private var lastTab = GuideTab.SETUP

    /** 註冊按鍵與 client tick；只能在 client entrypoint 呼叫一次。 */
    fun register() {
        if (registered) return
        registered = true
        openKeyBinding = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                MinecraftGuideScreenKeys.OPEN_KEY,
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.code,
                MinecraftClientConfigScreenKeys.KEY_CATEGORY,
            ),
        )
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    /** 從指令開啟畫面；延到下一個 client tick，避免指令執行後關閉聊天畫面時一併關掉此畫面。 */
    fun openFromCommand() {
        openRequested = true
    }

    /**
     * 從其他畫面開啟，返回時回到該畫面。
     *
     * @param returnScreen 返回時回到的畫面。
     */
    fun openFrom(returnScreen: Screen) {
        open(MinecraftClient.getInstance(), returnScreen)
    }

    /**
     * 開啟指令要求的畫面，並消耗按鍵事件；其他畫面開啟時按鍵不搶占使用者輸入。
     *
     * @param client 本次 tick 的客戶端。
     */
    private fun tick(client: MinecraftClient) {
        if (openRequested) {
            openRequested = false
            if (client.world != null) open(client, returnScreen = null)
        }
        while (openKeyBinding.wasPressed()) {
            if (client.currentScreen == null) open(client, returnScreen = null)
        }
    }

    /**
     * 開啟畫面。
     *
     * @param client 客戶端。
     * @param returnScreen 返回時回到的畫面；關回遊戲時為 null。
     */
    private fun open(client: MinecraftClient, returnScreen: Screen?) {
        client.setScreen(
            GuideScreen(
                tileFaces = tileFaces,
                hudPreview = hudPreview,
                tileNames = tileNames,
                tileAssets = tileAssets,
                openAction = ::openAction,
                returnScreen = returnScreen,
                tab = lastTab,
                onTabChanged = { lastTab = it },
            ),
        )
    }

    /**
     * 開啟按鈕對應的畫面，返回時回到「如何開始」。
     *
     * @param action 按鈕對應的畫面。
     * @param returnScreen 「如何開始」畫面。
     */
    private fun openAction(action: GuideAction, returnScreen: Screen) {
        when (action) {
            GuideAction.HISTORY -> historyScreens.openFrom(returnScreen)
            GuideAction.CLIENT_CONFIG -> configScreens.openFrom(returnScreen)
            GuideAction.RULE_BASICS -> ruleCatalogueScreens.openFrom(returnScreen = returnScreen, categoryId = BuiltInRuleCatalogueCategoryIds.BASICS)
        }
    }
}
