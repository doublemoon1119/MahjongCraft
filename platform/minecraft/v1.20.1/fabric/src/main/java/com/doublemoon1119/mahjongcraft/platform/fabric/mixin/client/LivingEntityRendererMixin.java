package com.doublemoon1119.mahjongcraft.platform.fabric.mixin.client;

import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.SceneEntityTranslucency;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 讓不在世界中的場景生物以指定不透明度畫出；只在 {@link SceneEntityTranslucency} 生效期間改變原版行為。 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    /** 原版繪製方法的描述。 */
    private static final String RENDER =
        "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    /** 生效時改用原版畫看得見的隱形生物所用的半透明圖層。 */
    @ModifyArg(
        method = RENDER,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;"
        ),
        index = 2
    )
    private boolean mahjongcraft$translucentLayer(boolean translucent) {
        return translucent || SceneEntityTranslucency.isActive();
    }

    /** 生效時以指定不透明度取代原版固定的不透明度。 */
    @ModifyArg(
        method = RENDER,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"
        ),
        index = 7
    )
    private float mahjongcraft$sceneAlpha(float alpha) {
        return SceneEntityTranslucency.alpha(alpha);
    }

    /** 生效時不畫盔甲、手持物品等附加圖層；原版只在旁觀者模式略過它們。 */
    @ModifyExpressionValue(
        method = RENDER,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSpectator()Z")
    )
    private boolean mahjongcraft$skipFeatures(boolean spectator) {
        return spectator || SceneEntityTranslucency.isActive();
    }
}
