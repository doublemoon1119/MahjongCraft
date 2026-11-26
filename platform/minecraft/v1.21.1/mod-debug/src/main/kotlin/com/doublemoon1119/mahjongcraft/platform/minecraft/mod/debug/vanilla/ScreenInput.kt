package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla

import net.minecraft.client.gui.screens.Screen

/**
 * 對畫面送出一次垂直的滑鼠滾輪捲動。
 *
 * @param screen 目前畫面。
 * @param mouseX 滑鼠的水平位置。
 * @param mouseY 滑鼠的垂直位置。
 * @param amount 捲動量，往上為正。
 */
fun scrollScreen(screen: Screen, mouseX: Double, mouseY: Double, amount: Double) {
    screen.mouseScrolled(mouseX, mouseY, 0.0, amount)
}
