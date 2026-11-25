package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * 本 mod 畫面的共同基底，讓畫面以相同的寫法畫背景、畫元件與處理滾輪。
 *
 * 原版畫面的背景、繪製與滾輪方法在不同 Minecraft 版本有不同的簽章與分工；畫面改用這裡的方法，由這個類別轉接到這個版本
 * 的原版方法。畫面在自己的 `render` 中依序呼叫 [renderScreenBackground]（需要背景時）、畫自己的內容，最後呼叫
 * [renderWidgets]。
 *
 * 這個版本的原版 `render` 會先畫背景，而背景的模糊效果每幀只能畫一次；因此原版自動畫背景的呼叫在這裡不做任何事，
 * 背景只由 [renderScreenBackground] 畫出。
 *
 * @param title 畫面標題。
 */
abstract class MahjongScreen(title: Component) : Screen(title) {
    /**
     * 畫出原版的畫面背景。
     *
     * @param context 繪製環境。
     * @param mouseX 滑鼠的水平位置。
     * @param mouseY 滑鼠的垂直位置。
     * @param delta 這一幀的時間進度。
     */
    protected fun renderScreenBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderBackground(context, mouseX, mouseY, delta)
    }

    /**
     * 在標題下方到底部按鈕上方之間畫出清單內容區的底：這個版本的原版在模糊背景上替清單墊一層半透明深色底並在上下畫分隔線，這裡畫出同樣的底，讓文字在明亮的背景上仍然清楚。
     *
     * 只用在以清單或表單為主的畫面；只有幾個按鈕的畫面與整個畫面就是一個視圖的畫面不畫。
     *
     * @param context 繪製環境。
     */
    protected fun renderContentBackground(context: GuiGraphics) {
        val top = ContentBackground.TOP
        val bottom = ContentBackground.bottom(height)
        val inWorld = minecraft?.level != null
        RenderSystem.enableBlend()
        context.blit(if (inWorld) INWORLD_LIST_BACKGROUND else LIST_BACKGROUND, 0, top, width.toFloat(), bottom.toFloat(), width, bottom - top, TILE_SIZE, TILE_SIZE)
        context.blit(if (inWorld) INWORLD_HEADER_SEPARATOR else HEADER_SEPARATOR, 0, top - SEPARATOR_HEIGHT, 0f, 0f, width, SEPARATOR_HEIGHT, TILE_SIZE, SEPARATOR_HEIGHT)
        context.blit(if (inWorld) INWORLD_FOOTER_SEPARATOR else FOOTER_SEPARATOR, 0, bottom, 0f, 0f, width, SEPARATOR_HEIGHT, TILE_SIZE, SEPARATOR_HEIGHT)
        RenderSystem.disableBlend()
    }

    /**
     * 畫出加入這個畫面的按鈕等元件，不畫背景。
     *
     * @param context 繪製環境。
     * @param mouseX 交給元件的滑鼠水平位置。
     * @param mouseY 交給元件的滑鼠垂直位置。
     * @param delta 這一幀的時間進度。
     */
    protected fun renderWidgets(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
    }

    final override fun renderBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) = Unit

    final override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean = mouseWheelScrolled(mouseX, mouseY, scrollY)

    /**
     * 滑鼠滾輪垂直捲動；預設交給畫面中的元件處理，水平捲動量為 0。
     *
     * @param mouseX 滑鼠的水平位置。
     * @param mouseY 滑鼠的垂直位置。
     * @param amount 捲動量，往上為正。
     * @return 已處理時為 true。
     */
    protected open fun mouseWheelScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean = super.mouseScrolled(mouseX, mouseY, 0.0, amount)

    private companion object {
        /** 原版主選單中清單的底圖。 */
        val LIST_BACKGROUND: ResourceLocation = ResourceLocation.withDefaultNamespace("textures/gui/menu_list_background.png")

        /** 原版遊戲中清單的底圖。 */
        val INWORLD_LIST_BACKGROUND: ResourceLocation = ResourceLocation.withDefaultNamespace("textures/gui/inworld_menu_list_background.png")

        /** 底圖重複鋪排的單位大小。 */
        const val TILE_SIZE: Int = 32

        /** 分隔線的高度。 */
        const val SEPARATOR_HEIGHT: Int = 2
    }
}
