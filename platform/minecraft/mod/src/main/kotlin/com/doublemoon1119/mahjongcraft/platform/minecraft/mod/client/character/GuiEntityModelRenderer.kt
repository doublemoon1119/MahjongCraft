package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.framePartialTick
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.mulPose
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.renderEntityFollowingLook
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.LightTexture
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

/** 房間模型沿用玩家的預覽版面；生物使用原版入口，其他實體使用相同座標方向。 */
internal object GuiEntityModelRenderer {
    /**
     * 依卡片原有基線與游標偏移繪製；暫時展示狀態在成功或失敗時都還原。
     *
     * @param entity 已載入的本人或獨立預覽模型。
     * @param context GUI 繪製上下文。
     * @param area 卡片上畫人物的範圍與放大倍數。
     * @param mouseX 游標水平位置。
     * @param mouseY 游標垂直位置。
     * @return 尺寸有效並完成繪製時為 true。
     */
    fun render(entity: Entity, context: GuiGraphics, area: RoomFigureArea, mouseX: Int, mouseY: Int): Boolean {
        if (area.width <= 0 || !entity.bbHeight.isFinite() || !entity.bbWidth.isFinite() || entity.bbHeight <= 0f || entity.bbWidth <= 0f || entity.bbHeight > 128f || entity.bbWidth > 128f) return false
        val view = RoomModelPreviewLayout.forCard(area, mouseX, mouseY)
        val scale = if (entity is Player) view.scale else (view.scale * minOf(1f, RoomFigureArea.MODEL_EXTENT / maxOf(entity.bbHeight, entity.bbWidth))).toInt().coerceAtLeast(1)
        val client = Minecraft.getInstance()
        val matrices = context.pose()
        val originalEntry = matrices.last()
        val graphics = client.options.graphicsMode()
        val originalGraphics = graphics.get()
        context.flush()
        CharacterPreviewRenderState().use {
            try {
                if (entity is LivingEntity) {
                    LivingPose(entity).use {
                        renderEntityFollowingLook(context, view.centerX, view.feetY, scale, view.lookX, view.lookY, entity)
                    }
                } else {
                    matrices.pushPose()
                    matrices.translate(view.centerX.toDouble(), view.feetY.toDouble(), 50.0)
                    matrices.mulPose(view.scaleMatrix(scale))
                    matrices.mulPose(view.modelRotation())
                    Lighting.setupForEntityInInventory()
                    val dispatcher = client.entityRenderDispatcher
                    dispatcher.overrideCameraOrientation(view.pitchRotation().conjugate())
                    dispatcher.setRenderShadow(false)
                    val consumers = client.renderBuffers().bufferSource()
                    // 此版本原版物品欄同樣使用此入口選擇預覽目標。
                    @Suppress("DEPRECATION")
                    RenderSystem.runAsFancy {
                        AutoCloseable { consumers.endBatch() }.use {
                            dispatcher.render(entity, 0.0, 0.0, 0.0, view.bodyYaw(), client.framePartialTick, matrices, consumers, LightTexture.FULL_BRIGHT)
                        }
                    }
                }
            } finally {
                while (matrices.last() !== originalEntry && !matrices.clear()) matrices.popPose()
                if (graphics.get() != originalGraphics) graphics.set(originalGraphics)
                Lighting.setupFor3DItems()
            }
        }
        return true
    }

    /**
     * 原版生物預覽會暫改朝向；此保護在 renderer 拋錯時也恢復本人。
     *
     * @property entity 此次預覽的生物。
     */
    private class LivingPose(private val entity: LivingEntity) : AutoCloseable {
        /** 進入前的身體朝向。 */
        private val bodyYaw = entity.yBodyRot

        /** 進入前的水平朝向。 */
        private val yaw = entity.yRot

        /** 進入前的垂直朝向。 */
        private val pitch = entity.xRot

        /** 進入前的頭部朝向。 */
        private val headYaw = entity.yHeadRot

        /** 進入前的頭部插值朝向。 */
        private val previousHeadYaw = entity.yHeadRotO

        /** 即使原版預覽失敗，也還原它會暫時修改的五項角度。 */
        override fun close() {
            entity.yBodyRot = bodyYaw
            entity.setYRot(yaw)
            entity.setXRot(pitch)
            entity.yHeadRot = headYaw
            entity.yHeadRotO = previousHeadYaw
        }
    }
}
