package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceRollAnimation
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
import org.joml.Quaternionf

/** 使用單一骰子 item model 呈現指定朝上點數的 client renderer。 */
class MahjongDiceEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<MahjongDiceEntity>(context) {
    /** 共用 Vanilla item renderer，避免建立另一套 cube model loader。 */
    private val itemRenderer = context.itemRenderer

    /** 依 entity yaw 與點數姿態旋轉模型，並讓骰子底面貼齊 entity 位置。 */
    override fun render(
        entity: MahjongDiceEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
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
                elapsedTicks = entity.world.time.toDouble() + tickDelta - entity.animationStartGameTime,
                startOffset = entity.animationStartOffset,
            )
        } else {
            null
        }
        matrices.push()
        if (animationFrame != null) {
            matrices.translate(animationFrame.offset.x, animationFrame.offset.y, animationFrame.offset.z)
        }
        matrices.translate(0.0, MahjongDiceEntity.SIZE / 2.0, 0.0)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.yaw + 180.0f))
        if (animationFrame != null) {
            val rotation = animationFrame.rotation
            matrices.multiply(
                Quaternionf(rotation.x.toFloat(), rotation.y.toFloat(), rotation.z.toFloat(), rotation.w.toFloat()),
            )
        }
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.point.xRotationDegrees))
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.point.yRotationDegrees))
        matrices.scale(DICE_MODEL_SCALE, DICE_MODEL_SCALE, DICE_MODEL_SCALE)
        matrices.translate(0.0, DICE_MODEL_CENTER_OFFSET_Y, 0.0)
        itemRenderer.renderItem(
            DICE_STACK,
            ModelTransformationMode.HEAD,
            light,
            OverlayTexture.DEFAULT_UV,
            matrices,
            vertexConsumers,
            entity.world,
            entity.id,
        )
        matrices.pop()
    }

    /** ItemRenderer 自行解析材質，因此 entity renderer 不提供單一 texture。 */
    override fun getTexture(entity: MahjongDiceEntity): Identifier? = null

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
