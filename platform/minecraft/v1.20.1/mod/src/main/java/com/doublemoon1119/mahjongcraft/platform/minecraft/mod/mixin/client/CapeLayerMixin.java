package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.mixin.client;

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.SceneEntityTranslucency;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 讓不在世界中的場景玩家的披風跟著 {@link SceneEntityTranslucency} 的不透明度畫出；沒有生效時照原版繪製。 */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
    /** 原版繪製方法的描述。 */
    private static final String RENDER =
        "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V";

    /** 生效時把原版不透明的披風圖層換成半透明圖層。 */
    @WrapOperation(
        method = RENDER,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderType;entitySolid(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"
        )
    )
    private RenderType mahjongcraft$translucentCape(ResourceLocation texture, Operation<RenderType> original) {
        return SceneEntityTranslucency.isActive() ? SceneEntityTranslucency.renderType(texture) : original.call(texture);
    }

    /** 生效時以指定不透明度畫出披風。 */
    @WrapOperation(
        method = RENDER,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/PlayerModel;renderCloak(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"
        )
    )
    private void mahjongcraft$cloakAlpha(
        PlayerModel<?> model,
        PoseStack poseStack,
        VertexConsumer consumer,
        int light,
        int overlay,
        Operation<Void> original
    ) {
        if (SceneEntityTranslucency.isActive()) {
            ((PlayerModelAccessor) model).mahjongcraft$cloak()
                .render(poseStack, consumer, light, overlay, 1f, 1f, 1f, SceneEntityTranslucency.alpha(1f));
        } else {
            original.call(model, poseStack, consumer, light, overlay);
        }
    }
}
