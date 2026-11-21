package com.doublemoon1119.mahjongcraft.platform.fabric.mixin.client;

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.HiddenDebugHitboxEntity;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 碰撞箱除錯顯示略過 {@link HiddenDebugHitboxEntity}；其他實體照原版繪製。 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    /** 實體不是 {@link HiddenDebugHitboxEntity} 時才畫碰撞箱。 */
    @WrapWithCondition(
        method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;F)V"
        )
    )
    private boolean mahjongcraft$skipHiddenDebugHitbox(
        PoseStack poseStack,
        VertexConsumer vertexConsumer,
        Entity entity,
        float partialTick
    ) {
        return !(entity instanceof HiddenDebugHitboxEntity);
    }
}
