package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongVisualEffectKeys
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationEffectEntity
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.entity.model.TridentEntityModel
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier

/**
 * 對局中胡牌特效實體的畫法：依同步的絕對時間換算經過的 tick，交給 [WinCelebrationEffectGeometry] 畫出。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 */
class WinCelebrationEffectEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<WinCelebrationEffectEntity>(context) {
    /** 特效本身的畫法。 */
    private val geometry = WinCelebrationEffectGeometry(TridentEntityModel(context.getPart(EntityModelLayers.TRIDENT)))

    /** 依同步的絕對時間畫出特效；不是胡牌特效或不在播放時間內時不畫。 */
    override fun render(
        entity: WinCelebrationEffectEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        if (entity.effectKey != MahjongVisualEffectKeys.WIN_CELEBRATION || !entity.isActive(tickDelta)) return
        geometry.render(
            elapsedTicks = entity.world.time.toDouble() + tickDelta - entity.startGameTime,
            seed = entity.animationSeed,
            matrices = matrices,
            vertexConsumers = vertexConsumers,
            light = light,
        )
    }

    /** 純色線段不使用材質。 */
    override fun getTexture(entity: WinCelebrationEffectEntity): Identifier? = null
}
