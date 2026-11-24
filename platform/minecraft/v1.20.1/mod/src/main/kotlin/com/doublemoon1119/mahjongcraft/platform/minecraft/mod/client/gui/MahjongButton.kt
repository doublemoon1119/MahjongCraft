package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.network.chat.Component

/**
 * 本 mod 按鈕的基底：子類別在 [adjustTooltipPositioner] 調整提示框的位置，各版本原版決定提示框位置的方式由這個類別處理。
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
    /**
     * 調整原版決定的提示框位置；預設不調整。
     *
     * @param base 原版的提示框定位方式。
     * @return 實際使用的提示框定位方式。
     */
    protected open fun adjustTooltipPositioner(base: ClientTooltipPositioner): ClientTooltipPositioner = base

    final override fun createTooltipPositioner(): ClientTooltipPositioner = adjustTooltipPositioner(super.createTooltipPositioner())
}
