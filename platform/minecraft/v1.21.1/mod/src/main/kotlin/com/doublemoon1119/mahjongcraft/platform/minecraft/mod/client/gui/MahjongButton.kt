package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.client.gui.screens.inventory.tooltip.MenuTooltipPositioner
import net.minecraft.network.chat.Component
import java.time.Duration

/**
 * 本 mod 按鈕的基底。
 *
 * - 子類別在 [renderContents] 畫按鈕，在 [adjustTooltipPositioner] 調整提示框的位置。
 * - 各版本原版決定提示框位置的方式由這個類別處理。
 * - 這個版本的原版把提示框位置的決定藏在按鈕內部，因此提示框由這個類別自己保存，並以與原版相同的條件與延遲交給畫面。
 *
 * @param x 按鈕左界。
 * @param y 按鈕上界。
 * @param width 按鈕寬度。
 * @param height 按鈕高度。
 * @param message 按鈕文字。
 * @param onPress 按下按鈕時執行的動作。
 * @param narration 產生旁白文字的方式。
 */
internal abstract class MahjongButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    onPress: OnPress,
    narration: CreateNarration,
) : Button(x, y, width, height, message, onPress, narration) {
    /** 這個按鈕的提示框，沒有時為 null。 */
    private var buttonTooltip: Tooltip? = null

    /** 開始指向按鈕後到顯示提示框的延遲。 */
    private var buttonTooltipDelay: Duration = Duration.ZERO

    /** 上一幀是否符合顯示提示框的條件。 */
    private var tooltipWanted = false

    /** 最近一次開始符合顯示條件的時間，以毫秒計。 */
    private var tooltipWantedSinceMillis = 0L

    /**
     * 畫出按鈕。預設畫原版按鈕。
     *
     * @param context 繪製環境。
     * @param mouseX 滑鼠 X 座標。
     * @param mouseY 滑鼠 Y 座標。
     * @param delta 這一幀在目前遊戲刻中的進度。
     */
    protected open fun renderContents(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderWidget(context, mouseX, mouseY, delta)
    }

    /**
     * 調整原版決定的提示框位置。預設不調整。
     *
     * @param base 原版的提示框定位方式。
     * @return 實際使用的提示框定位方式。
     */
    protected open fun adjustTooltipPositioner(base: ClientTooltipPositioner): ClientTooltipPositioner = base

    final override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderContents(context, mouseX, mouseY, delta)
        refreshTooltip()
    }

    override fun setTooltip(tooltip: Tooltip?) {
        buttonTooltip = tooltip
    }

    override fun getTooltip(): Tooltip? = buttonTooltip

    override fun setTooltipDelay(delay: Duration) {
        buttonTooltipDelay = delay
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        super.updateWidgetNarration(output)
        buttonTooltip?.updateNarration(output)
    }

    /** 依原版的條件把提示框交給目前畫面，在下一次畫面繪製時顯示。 */
    private fun refreshTooltip() {
        val tooltip = buttonTooltip
        if (tooltip == null) {
            tooltipWanted = false
            return
        }
        val keyboardFocused = isFocused && Minecraft.getInstance().lastInputType.isKeyboard
        val wanted = isHovered || keyboardFocused
        if (wanted && !tooltipWanted) tooltipWantedSinceMillis = Util.getMillis()
        tooltipWanted = wanted
        if (!wanted || Util.getMillis() - tooltipWantedSinceMillis <= buttonTooltipDelay.toMillis()) return
        val screen = Minecraft.getInstance().screen ?: return
        val base = if (!isHovered && keyboardFocused) BelowOrAboveWidgetTooltipPositioner(rectangle) else MenuTooltipPositioner(rectangle)
        screen.setTooltipForNextRenderPass(tooltip, adjustTooltipPositioner(base), isFocused)
    }
}
