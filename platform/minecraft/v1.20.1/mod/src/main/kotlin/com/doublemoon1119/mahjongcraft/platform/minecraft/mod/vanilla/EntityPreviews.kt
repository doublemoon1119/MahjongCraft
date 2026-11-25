package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.LivingEntity

/**
 * 以原版物品欄預覽玩家的方式畫出生物，讓它的頭與身體朝向指定方向。
 *
 * @param context 繪製環境。
 * @param centerX 生物中心的水平位置。
 * @param feetY 生物腳底的垂直位置。
 * @param scale 縮放倍率。
 * @param lookX 視線方向相對於生物中心的水平位移。
 * @param lookY 視線方向相對於生物中心的垂直位移。
 * @param entity 要畫的生物。
 */
fun renderEntityFollowingLook(
    context: GuiGraphics,
    centerX: Int,
    feetY: Int,
    scale: Int,
    lookX: Float,
    lookY: Float,
    entity: LivingEntity,
) {
    InventoryScreen.renderEntityInInventoryFollowsMouse(context, centerX, feetY, scale, lookX, lookY, entity)
}
