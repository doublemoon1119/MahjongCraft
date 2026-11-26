package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
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

/**
 * 依這個版本原版物品欄畫生物的步驟畫出任意實體，供原版入口不接受的非生物使用。
 *
 * - 步驟與原版相同：放大並翻轉 Z 軸、套用模型旋轉、設定物品欄打光與相機朝向、關閉陰影後送出繪製，最後恢復打光。
 * - 繪製失敗時同樣送出已寫入的頂點，並恢復陰影、座標轉換與打光。
 * - 放大的方式見 [scaleLikeEntityPreview]。
 * - 這個版本的原版以視線旋轉的反向再轉半圈作為相機朝向。
 * - 與原版不同，水平朝向與 tick 進度由呼叫端指定，因為非生物沒有身體朝向可以暫時改寫。
 *
 * @param matrices 座標轉換。返回時恢復成呼叫前的狀態。
 * @param consumers 頂點緩衝，繪製後立即送出。
 * @param x 實體原點的水平位置。
 * @param y 實體原點的垂直位置。
 * @param scale 縮放倍率。
 * @param rotation 放大之後套用的模型旋轉。
 * @param lookRotation 視線的垂直旋轉，用來決定相機朝向；null 時不改變相機朝向。
 * @param entity 要畫的實體。
 * @param yaw 實體的水平朝向。
 * @param partialTick 本幀的 tick 進度。
 */
fun renderEntityLikeInventory(
    matrices: PoseStack,
    consumers: MultiBufferSource.BufferSource,
    x: Float,
    y: Float,
    scale: Float,
    rotation: Quaternionf,
    lookRotation: Quaternionf?,
    entity: Entity,
    yaw: Float,
    partialTick: Float,
) {
    val dispatcher = Minecraft.getInstance().entityRenderDispatcher
    matrices.pushPose()
    try {
        matrices.translate(x.toDouble(), y.toDouble(), 50.0)
        scaleLikeEntityPreview(matrices, scale)
        matrices.mulPose(rotation)
        Lighting.setupForEntityInInventory()
        if (lookRotation != null) dispatcher.overrideCameraOrientation(lookRotation.conjugate(Quaternionf()).rotateY(Math.PI.toFloat()))
        dispatcher.setRenderShadow(false)
        // 原版物品欄同樣以這個方法暫時使用精緻畫質繪製。
        @Suppress("DEPRECATION")
        RenderSystem.runAsFancy {
            dispatcher.render(entity, 0.0, 0.0, 0.0, yaw, partialTick, matrices, consumers, LightTexture.FULL_BRIGHT)
        }
    } finally {
        consumers.endBatch()
        dispatcher.setRenderShadow(true)
        matrices.popPose()
        Lighting.setupFor3DItems()
    }
}

/**
 * 依這個版本原版物品欄的方式放大座標：三軸放大 [scale] 倍並翻轉 Z 軸。
 *
 * - 這個版本的原版以 `PoseStack.scale` 放大，法線維持單位長度。
 * - 不能改以縮放矩陣乘上座標轉換：法線會跟著縮短，而有些載入器寫入烘焙模型的頂點時不會再把法線恢復成單位長度，
 *   模型因此變暗。
 *
 * @param matrices 要放大的座標轉換。
 * @param scale 縮放倍率。
 */
internal fun scaleLikeEntityPreview(matrices: PoseStack, scale: Float) {
    matrices.scale(scale, scale, -scale)
}
