package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.LivingEntity
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 以原版物品欄預覽玩家的方式畫出生物，讓它的頭與身體朝向指定方向。
 *
 * - 這個版本的原版方法以一個裁切框的中心擺放生物的身體中心，這裡換算出讓腳底落在 [feetY] 的裁切框。
 * - 裁切框比生物大一圈，不會切掉轉動中的頭與手。
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
    val height = entity.bbHeight * scale
    val boxCenterY = (feetY - height / 2).roundToInt()
    val halfExtent = ceil(height).toInt()
    val boxTop = boxCenterY - halfExtent
    // 原版以裁切框中心減去滑鼠位置作為視線位移，因此把位移換回滑鼠位置。
    InventoryScreen.renderEntityInInventoryFollowsMouse(
        context,
        centerX - halfExtent,
        boxTop,
        centerX + halfExtent,
        boxTop + 2 * halfExtent,
        scale,
        0f,
        centerX - lookX,
        boxCenterY - lookY,
        entity,
    )
}
