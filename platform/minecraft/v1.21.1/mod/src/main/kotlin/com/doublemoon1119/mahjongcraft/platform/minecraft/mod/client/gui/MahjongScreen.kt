package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

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
}
