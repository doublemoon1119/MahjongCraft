package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.mixin.client;

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.SceneEntityTranslucency;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 讓不在世界中的場景生物以指定不透明度畫出；只在 {@link SceneEntityTranslucency} 生效期間改變原版行為。 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    /** 原版繪製方法的描述。 */
    private static final String RENDER =
        "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    /** 生效時走原版畫看得見的隱形生物的半透明分支。 */
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

    /** 生效時把半透明分支的原版圖層換成 {@link SceneEntityTranslucency#renderType} 的圖層。 */
    @WrapOperation(
        method = "getRenderType",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderType;itemEntityTranslucentCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"
        )
    )
    private RenderType mahjongcraft$sceneTranslucentLayer(ResourceLocation texture, Operation<RenderType> original) {
        return SceneEntityTranslucency.isActive() ? SceneEntityTranslucency.renderType(texture) : original.call(texture);
    }

    /** 生效時以指定不透明度取代原版 ARGB 顏色中固定的不透明度，紅綠藍不變。 */
    @ModifyArg(
        method = RENDER,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
        ),
        index = 4
    )
    private int mahjongcraft$sceneAlpha(int color) {
        float alpha = SceneEntityTranslucency.alpha(FastColor.ARGB32.alpha(color) / 255f);
        return FastColor.ARGB32.color(FastColor.as8BitChannel(alpha), color);
    }

    /** 生效時只畫能以半透明畫出的附加圖層，例如披風；盔甲、手持物品等其他圖層不畫。 */
    @WrapWithCondition(
        method = RENDER,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"
        )
    )
    private boolean mahjongcraft$translucentLayersOnly(
        RenderLayer<?, ?> layer,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int light,
        Entity entity,
        float limbSwing,
        float limbSwingAmount,
        float partialTick,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        return !SceneEntityTranslucency.isActive() || layer instanceof CapeLayer;
    }
}
