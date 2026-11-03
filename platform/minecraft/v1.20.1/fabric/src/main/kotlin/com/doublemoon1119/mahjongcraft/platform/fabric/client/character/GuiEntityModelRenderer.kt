package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.client.render.DiffuseLighting
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity

/** 房間模型沿用玩家的預覽版面；生物使用原版入口，其他實體使用相同座標方向。 */
internal object GuiEntityModelRenderer {
    /**
     * 依卡片原有基線與游標偏移繪製；暫時展示狀態在成功或失敗時都還原。
     *
     * @param entity 已載入的本人或獨立預覽模型。
     * @param context GUI 繪製上下文。
     * @param x 卡片左界。
     * @param y 卡片頂端。
     * @param size 卡片寬度，不是模型填滿區域。
     * @param mouseX 游標水平位置。
     * @param mouseY 游標垂直位置。
     * @return 尺寸有效並完成繪製時為 true。
     */
    fun render(entity: Entity, context: DrawContext, x: Int, y: Int, size: Int, mouseX: Int, mouseY: Int): Boolean {
        if (size <= 0 || !entity.height.isFinite() || !entity.width.isFinite() || entity.height <= 0f || entity.width <= 0f || entity.height > 128f || entity.width > 128f) return false
        val view = RoomModelPreviewLayout.forCard(x, y, size, mouseX, mouseY)
        val scale = if (entity is PlayerEntity) view.scale else (view.scale * minOf(1f, 1.8f / maxOf(entity.height, entity.width))).toInt().coerceAtLeast(1)
        val client = MinecraftClient.getInstance()
        val matrices = context.matrices
        val originalEntry = matrices.peek()
        val graphics = client.options.graphicsMode
        val originalGraphics = graphics.value
        context.draw()
        CharacterPreviewRenderState().use {
            try {
                if (entity is LivingEntity) {
                    LivingPose(entity).use {
                        InventoryScreen.drawEntity(context, view.centerX, view.feetY, scale, view.lookX, view.lookY, entity)
                    }
                } else {
                    matrices.push()
                    matrices.translate(view.centerX.toDouble(), view.feetY.toDouble(), 50.0)
                    matrices.multiplyPositionMatrix(view.scaleMatrix(scale))
                    matrices.multiply(view.modelRotation())
                    DiffuseLighting.method_34742()
                    val dispatcher = client.entityRenderDispatcher
                    dispatcher.setRotation(view.pitchRotation().conjugate())
                    dispatcher.setRenderShadows(false)
                    val consumers = client.bufferBuilders.entityVertexConsumers
                    // 此版本原版物品欄同樣使用此入口選擇預覽目標。
                    @Suppress("DEPRECATION")
                    RenderSystem.runAsFancy {
                        AutoCloseable { consumers.draw() }.use {
                            dispatcher.render(entity, 0.0, 0.0, 0.0, view.bodyYaw(), client.tickDelta, matrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE)
                        }
                    }
                }
            } finally {
                while (matrices.peek() !== originalEntry && !matrices.isEmpty) matrices.pop()
                if (graphics.value != originalGraphics) graphics.value = originalGraphics
                DiffuseLighting.enableGuiDepthLighting()
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
        private val bodyYaw = entity.bodyYaw

        /** 進入前的水平朝向。 */
        private val yaw = entity.yaw

        /** 進入前的垂直朝向。 */
        private val pitch = entity.pitch

        /** 進入前的頭部朝向。 */
        private val headYaw = entity.headYaw

        /** 進入前的頭部插值朝向。 */
        private val previousHeadYaw = entity.prevHeadYaw

        /** 即使原版預覽失敗，也還原它會暫時修改的五項角度。 */
        override fun close() {
            entity.bodyYaw = bodyYaw
            entity.yaw = yaw
            entity.pitch = pitch
            entity.headYaw = headYaw
            entity.prevHeadYaw = previousHeadYaw
        }
    }
}
