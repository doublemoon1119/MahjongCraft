package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceRollAnimation
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModItems
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf

/**
 * 使用單一骰子 item model 呈現指定朝上點數的 client renderer。
 *
 * @param context Minecraft entity renderer 共用資源。
 */
class MahjongDiceEntityRenderer(
    context: EntityRendererProvider.Context,
) : EntityRenderer<MahjongDiceEntity>(context) {
    /** 共用 Vanilla item renderer，避免建立另一套 cube model loader。 */
    private val itemRenderer = context.itemRenderer

    /** 依 entity yaw 與點數姿態旋轉模型，並讓骰子底面貼齊 entity 位置。 */
    override fun render(
        entity: MahjongDiceEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        // 沿用 vanilla Entity.isInvisible 當作「擲骰動畫尚未輪到、先隱形等待」的開關（骰子現在一
        // 生成就存在於世界，等 MahjongDiceEntity.startRoll 排定的動畫佇列 Wait step 到期才解除隱形，
        // 不再像過去那樣延後到真正該投擲的那一刻才生成 entity）；EntityRenderDispatcher 本身只用這個
        // 旗標控制陰影／除錯 hitbox，不會自動幫自訂 renderer 跳過 render()，理由同 MahjongTileEntityRenderer。
        if (entity.isInvisible) return
        val animationFrame = if (entity.rolling) {
            ROLL_ANIMATION.frame(
                seed = entity.animationSeed,
                elapsedTicks = entity.level().gameTime.toDouble() + tickDelta - entity.animationStartGameTime,
                startOffset = entity.animationStartOffset,
            )
        } else {
            null
        }
        matrices.pushPose()
        if (animationFrame != null) {
            matrices.translate(animationFrame.offset.x, animationFrame.offset.y, animationFrame.offset.z)
        }
        matrices.translate(0.0, MahjongDiceEntity.SIZE / 2.0, 0.0)
        matrices.mulPose(Axis.YP.rotationDegrees(-entity.yRot + 180.0f))
        if (animationFrame != null) {
            val rotation = animationFrame.rotation
            matrices.mulPose(
                Quaternionf(rotation.x.toFloat(), rotation.y.toFloat(), rotation.z.toFloat(), rotation.w.toFloat()),
            )
        }
        matrices.mulPose(Axis.XP.rotationDegrees(entity.point.xRotationDegrees))
        matrices.mulPose(Axis.YP.rotationDegrees(entity.point.yRotationDegrees))
        matrices.scale(DICE_MODEL_SCALE, DICE_MODEL_SCALE, DICE_MODEL_SCALE)
        matrices.translate(0.0, DICE_MODEL_CENTER_OFFSET_Y, 0.0)
        itemRenderer.renderStatic(
            DICE_STACK,
            ItemDisplayContext.HEAD,
            light,
            OverlayTexture.NO_OVERLAY,
            matrices,
            vertexConsumers,
            entity.level(),
            entity.id,
        )
        matrices.popPose()
    }

    /** ItemRenderer 自行解析材質，因此 entity renderer 不提供單一 texture；原版規定回傳值不可為空，因此回傳方塊材質圖集。 */
    override fun getTextureLocation(entity: MahjongDiceEntity): ResourceLocation = InventoryMenu.BLOCK_ATLAS

    companion object {
        /** 舊模型由 `10/16 block` 縮放成 `0.125 block` 所需比例。 */
        private const val DICE_MODEL_SCALE = 0.2f

        /** 將舊模型實際中心 `Y = 5` 對齊 item renderer 使用的 `Y = 8` 旋轉原點。 */
        private const val DICE_MODEL_CENTER_OFFSET_Y = 3.0 / 16.0

        /** 供所有骰子 entity 共用的渲染 stack。 */
        private val DICE_STACK = ItemStack(ModItems.MAHJONG_DICE)

        /** 所有骰子共用的確定性動畫計算器。 */
        private val ROLL_ANIMATION = DiceRollAnimation()
    }
}
