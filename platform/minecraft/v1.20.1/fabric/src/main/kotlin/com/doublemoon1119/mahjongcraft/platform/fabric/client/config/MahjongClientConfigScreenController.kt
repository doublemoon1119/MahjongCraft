package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.AutomaticControlDisplayResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutoSortHandPreferenceService
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutomaticControlUpdateCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue.RuleCatalogueScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * 集中註冊並處理 Client Config Screen 的快捷鍵與程式化開啟入口。
 *
 * @property configStore 本機客戶端設定儲存區。
 * @property automaticCoordinator 本局自動操作更新協調器。
 * @property displayResolver 自動操作顯示解析器。
 * @property preferenceService 手牌偏好保存服務。
 * @property historyScreenController 對局歷史畫面控制器。
 * @property ruleCatalogueScreenController 規則一覽畫面控制器。
 * @property hudPreviewRenderer HUD 位置編輯器的真實 HUD 預覽繪製。
 */
class MahjongClientConfigScreenController(
    private val configStore: MahjongClientConfigStore,
    private val automaticCoordinator: ClientAutomaticControlUpdateCoordinator,
    private val displayResolver: AutomaticControlDisplayResolver,
    private val preferenceService: ClientAutoSortHandPreferenceService,
    private val historyScreenController: HistoryScreenController,
    private val ruleCatalogueScreenController: RuleCatalogueScreenController,
    private val hudPreviewRenderer: MahjongHudPreviewRenderer,
) {
    /** 開啟 Client Config Screen 的按鍵綁定，預設不綁定；分號預設開啟麻將手冊。 */
    private lateinit var openKeyBinding: KeyMapping

    /** 開啟對局歷史的快捷鍵。 */
    private lateinit var historyKeyBinding: KeyMapping

    /** 開啟規則一覽的快捷鍵，預設不綁定。 */
    private lateinit var ruleCatalogueKeyBinding: KeyMapping

    /** 是否已完成事件註冊。 */
    private var registered = false

    /** 指令要求開啟、等待下一個 client tick 開啟。 */
    private var openRequested = false

    /** 註冊按鍵綁定與 client tick listener；只能在 client entrypoint 呼叫一次。 */
    fun register() {
        if (registered) return
        registered = true
        openKeyBinding = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                MinecraftClientConfigScreenKeys.OPEN_KEY,
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.value,
                MinecraftClientConfigScreenKeys.KEY_CATEGORY,
            ),
        )
        historyKeyBinding = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                MinecraftClientConfigScreenKeys.OPEN_HISTORY_KEY,
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.value,
                MinecraftClientConfigScreenKeys.KEY_CATEGORY,
            ),
        )
        ruleCatalogueKeyBinding = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                MinecraftClientConfigScreenKeys.OPEN_RULE_CATALOGUE_KEY,
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.value,
                MinecraftClientConfigScreenKeys.KEY_CATEGORY,
            ),
        )
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    /**
     * 立即開啟設定畫面，返回時回到 [returnScreen]。
     *
     * @param returnScreen 開啟設定畫面的畫面。
     */
    fun openFrom(returnScreen: Screen) {
        open(Minecraft.getInstance(), returnScreen)
    }

    /** 從指令開啟設定畫面；延到下一個 client tick，避免指令執行後關閉聊天畫面時一併關掉設定畫面。 */
    fun openFromCommand() {
        openRequested = true
    }

    /**
     * 開啟指令要求的設定畫面，並消耗快捷鍵事件；其他畫面開啟時快捷鍵不搶占使用者輸入。
     *
     * @param client 本次 tick 的客戶端。
     */
    private fun tick(client: Minecraft) {
        if (openRequested) {
            openRequested = false
            open(client, null)
        }
        while (openKeyBinding.consumeClick()) {
            if (client.screen == null) {
                open(client, null)
            }
        }
        while (historyKeyBinding.consumeClick()) {
            if (client.screen == null) {
                historyScreenController.openFromCommand()
            }
        }
        while (ruleCatalogueKeyBinding.consumeClick()) {
            if (client.screen == null) {
                ruleCatalogueScreenController.openGeneral()
            }
        }
    }

    /**
     * 建立設定畫面並保留返回目標。
     *
     * @param client 要開啟畫面的客戶端。
     * @param parent 返回目標；null 表示關回遊戲。
     */
    private fun open(client: Minecraft, parent: Screen?) {
        client.setScreen(screen(parent))
    }

    /**
     * 建立設定畫面但不開啟，供其他模組的畫面自己切換，例如模組清單的設定按鈕。
     *
     * @param parent 返回目標；null 表示關回遊戲。
     * @return 設定畫面。
     */
    fun screen(parent: Screen?): Screen = MahjongClientConfigScreen(
        parent = parent,
        configStore = configStore,
        automaticCoordinator = automaticCoordinator,
        displayResolver = displayResolver,
        preferenceService = preferenceService,
        hudPreviewRenderer = hudPreviewRenderer,
    )
}
