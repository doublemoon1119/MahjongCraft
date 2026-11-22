package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationEffectEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongVisualEffectKeys
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.TridentModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation

/**
 * 對局中胡牌特效實體的畫法：依同步的絕對時間換算經過的 tick，交給 [WinCelebrationEffectGeometry] 畫出。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 */
class WinCelebrationEffectEntityRenderer(
    context: EntityRendererProvider.Context,
) : EntityRenderer<WinCelebrationEffectEntity>(context) {
    /** 特效本身的畫法。 */
    private val geometry = WinCelebrationEffectGeometry(TridentModel(context.bakeLayer(ModelLayers.TRIDENT)))

    /** 依同步的絕對時間畫出特效；不是胡牌特效或不在播放時間內時不畫。 */
    override fun render(
        entity: WinCelebrationEffectEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        if (entity.effectKey != MahjongVisualEffectKeys.WIN_CELEBRATION || !entity.isActive(tickDelta)) return
        geometry.render(
            elapsedTicks = entity.level().gameTime.toDouble() + tickDelta - entity.startGameTime,
            seed = entity.animationSeed,
            matrices = matrices,
            vertexConsumers = vertexConsumers,
            light = light,
        )
    }

    /** 純色線段不使用材質。 */
    override fun getTextureLocation(entity: WinCelebrationEffectEntity): ResourceLocation? = null
}
