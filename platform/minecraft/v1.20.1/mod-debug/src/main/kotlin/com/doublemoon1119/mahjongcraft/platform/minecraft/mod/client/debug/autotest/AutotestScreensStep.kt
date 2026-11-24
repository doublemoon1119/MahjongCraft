package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.catalogue.RuleCatalogueScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.handbook.MahjongHandbookScreenController
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.network.chat.contents.TranslatableContents
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.seconds

/**
 * 從正式的入口逐一打開本 mod 的獨立畫面：截圖、往下捲動後再截一張，最後以 Esc 的方式關閉並確認回到遊戲畫面。
 *
 * 只檢查打開、捲動與關閉時沒有出錯；畫面內容由截圖確認。
 */
internal object AutotestScreensStep : AutotestStep {
    override val id: String = "screens"

    override suspend fun run(context: AutotestContext) {
        context.client.setScreen(null)
        val koin = GlobalContext.get()
        val screens = listOf(
            TourScreen("handbook", "HandbookScreen") { koin.get<MahjongHandbookScreenController>().openFromCommand() },
            TourScreen("guide", "GuideScreen") { koin.get<GuideScreenController>().openFromCommand() },
            TourScreen("config", "MahjongClientConfigScreen") { koin.get<MahjongClientConfigScreenController>().openFromCommand() },
            TourScreen("rule-catalogue", "RuleCatalogueScreen") { koin.get<RuleCatalogueScreenController>().openGeneral() },
        )
        screens.forEach { screen -> if (!context.tour(screen)) return }
        context.configSubScreens()
    }

    /**
     * 從設定畫面打開 HUD 版面編輯器再返回；改動一個設定後關閉，確認出現未儲存提示，選擇不儲存，設定檔不受影響。
     */
    private suspend fun AutotestContext.configSubScreens() {
        GlobalContext.get().get<MahjongClientConfigScreenController>().openFromCommand()
        if (!check("The config screen opens again", waitForScreen("MahjongClientConfigScreen"))) return
        if (!check("The HUD layout button is pressed", pressButton(MinecraftClientConfigScreenKeys.EDIT_HUD_LAYOUT))) return
        if (!check("The HUD layout editor opens", waitForScreen("MahjongHudLayoutEditorScreen"))) return
        waitTicks(SETTLE_TICKS)
        screenshot("hud-layout-editor")
        checkNotNull(client.screen).onClose()
        if (!check("The HUD layout editor returns to the config screen", waitForScreen("MahjongClientConfigScreen"))) return
        if (!check("A setting is changed", pressButton(MinecraftClientConfigScreenKeys.ENABLED))) return
        checkNotNull(client.screen).onClose()
        if (!check("Closing with a change asks about unsaved changes", waitForScreen("UnsavedChangesConfirmationScreen"))) return
        waitTicks(SETTLE_TICKS)
        screenshot("unsaved-changes")
        if (!check("The change is discarded", pressButton(MinecraftClientConfigScreenKeys.DISCARD_CHANGES))) return
        check("Discarding closes the config screen", waitUntil(OPEN_TIMEOUT) { client.screen == null }, "screen ${client.screen?.javaClass?.simpleName}")
    }

    /**
     * 等目前畫面換成指定類別的畫面。
     *
     * @param className 畫面類別名稱。
     * @return 在時限內出現時為 true。
     */
    private suspend fun AutotestContext.waitForScreen(className: String): Boolean = waitUntil(OPEN_TIMEOUT) { client.screen?.javaClass?.simpleName == className }

    /**
     * 按下目前畫面中文字為指定翻譯鍵的第一個按鈕，與玩家點擊相同。
     *
     * @param key 按鈕文字的翻譯鍵。
     * @return 找到並按下時為 true。
     */
    private fun AutotestContext.pressButton(key: String): Boolean {
        val button = client.screen?.let { findButton(it, key) } ?: return false
        button.onPress()
        return true
    }

    /**
     * 在畫面元件中找文字為指定翻譯鍵的按鈕，包含清單與分頁中的元件。
     *
     * @param listener 畫面或容器元件。
     * @param key 按鈕文字的翻譯鍵。
     * @return 找到的按鈕；沒有時為 null。
     */
    private fun findButton(listener: GuiEventListener, key: String): AbstractButton? {
        if (listener is AbstractButton && listener.active && (listener.message.contents as? TranslatableContents)?.key == key) return listener
        return (listener as? ContainerEventHandler)?.children()?.firstNotNullOfOrNull { findButton(it, key) }
    }

    /**
     * 打開一個畫面，截圖、捲動後再截圖，然後關閉。
     *
     * @param screen 要巡覽的畫面。
     * @return 打開與關閉都成功時為 true。
     */
    private suspend fun AutotestContext.tour(screen: TourScreen): Boolean {
        screen.open()
        val opened = waitUntil(OPEN_TIMEOUT) { client.screen?.javaClass?.simpleName == screen.className }
        if (!check("The ${screen.name} screen opens", opened, "screen ${client.screen?.javaClass?.simpleName}")) return false
        waitTicks(SETTLE_TICKS)
        screenshot("${screen.name}-opened")
        val current = checkNotNull(client.screen)
        current.mouseScrolled(current.width / 2.0, current.height / 2.0, SCROLL_DELTA)
        waitTicks(SETTLE_TICKS)
        screenshot("${screen.name}-scrolled")
        current.onClose()
        return check("The ${screen.name} screen closes back to the game", waitUntil(OPEN_TIMEOUT) { client.screen == null }, "screen ${client.screen?.javaClass?.simpleName}")
    }

    /**
     * 一個要巡覽的畫面。
     *
     * @property name 截圖與檢查使用的名稱。
     * @property className 打開後應該出現的畫面類別名稱。
     * @property open 從正式入口打開畫面。
     */
    private class TourScreen(val name: String, val className: String, val open: () -> Unit)

    /** 等畫面打開或關閉的最長時間。 */
    private val OPEN_TIMEOUT = 5.seconds

    /** 打開或捲動後讓畫面完成一次繪製的刻數，只影響截圖時機。 */
    private const val SETTLE_TICKS = 2

    /** 每次往下捲動的量。 */
    private const val SCROLL_DELTA = -10.0
}
