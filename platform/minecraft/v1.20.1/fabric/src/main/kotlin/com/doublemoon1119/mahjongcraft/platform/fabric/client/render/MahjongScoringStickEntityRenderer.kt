package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongScoringStickItem
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis

/** 使用既有點棒 item model 呈現面額的 client renderer；面額切換交給 vanilla item model predicate 解析。 */
class MahjongScoringStickEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<MahjongScoringStickEntity>(context) {
    /** 共用 Vanilla item renderer，避免建立另一套點棒模型格式。 */
    private val itemRenderer = context.itemRenderer

    /** 依 entity yaw 旋轉模型，並讓點棒底面貼齊 entity 位置。 */
    override fun render(
        entity: MahjongScoringStickEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        matrices.push()
        matrices.translate(0.0, MahjongScoringStickEntity.STICK_HEIGHT / 2.0, 0.0)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.yaw + 180.0f))
        matrices.translate(0.0, MODEL_Y_CENTER_COMPENSATION, 0.0)
        itemRenderer.renderItem(
            stickStacks.getValue(entity.denomination),
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

    /** ItemRenderer 依 model predicate 自行解析材質，因此 entity renderer 不提供單一 texture。 */
    override fun getTexture(entity: MahjongScoringStickEntity): Identifier? = null

    companion object {
        /**
         * 補償點棒模型 Y 軸幾何中心偏離標準方塊中心（16 單位模型空間的 Y=8）的位移，單位已換算成
         * 最終世界座標（見下方推導），可以直接在呼叫 [itemRenderer] 之前 `translate`。
         *
         * `mahjong_scoring_stick_base.json` 的 `body` 元素 Y 範圍是 `[0, 0.5]`（16 單位模型空間），
         * 中心在 `0.25`，不像牌／骰子那樣本來就落在標準中心 `8`——牌的元素 Y 範圍 `[0, 16]` 中心正好
         * 是 8。原始補償量是 `(8 - 0.25) / 16` 個模型單位。
         *
         * 這裡**不能**直接照抄骰子 [MahjongDiceEntityRenderer] 的 `DICE_MODEL_CENTER_OFFSET_Y` 用法——
         * 骰子的縮放是 renderer 自己呼叫 `matrices.scale(...)`，補償 translate 寫在 scale 呼叫之後，
         * 兩者都在同一個 [itemRenderer] 呼叫之外，補償量不需要另外乘上縮放比例。點棒的縮放（`0.4`）
         * 卻是烘焙在 `mahjong_scoring_stick_base.json` 的 `head` display 區塊裡，只會在
         * [itemRenderer] 內部才套用；這段補償 translate 只能寫在呼叫 [itemRenderer] **之前**（外層,
         * 縮放套用之前），因此必須自行乘上同一個 `0.4` 縮放比例，才會換算成跟 [itemRenderer] 內部縮放
         * 後幾何等效的世界座標位移——先前漏了這一步，導致點棒整根浮在正確位置上方將近半格。
         */
        private const val MODEL_Y_CENTER_COMPENSATION: Double = (8.0 - 0.25) / 16.0 * 0.4

        /** 每個面額共用一個只供渲染使用的 ItemStack。 */
        private val stickStacks: Map<MahjongScoringStickDenomination, ItemStack> =
            MahjongScoringStickDenomination.entries.associateWith { denomination ->
                ItemStack(ModItems.MAHJONG_SCORING_STICK).also { MahjongScoringStickItem.writeDenomination(it, denomination) }
            }
    }
}
