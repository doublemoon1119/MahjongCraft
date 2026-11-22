package com.doublemoon1119.mahjongcraft.platform.fabric.mixin.client;

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tile.ClientHandTileLayoutBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 讓本地手牌的 render 位移也參與原版視線選取。 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** 目前的客戶端，由原版 renderer 持有。 */
    @Shadow @Final private Minecraft minecraft;

    /** 每幀先固定本人牌張的顯示結果，視線選取與 renderer 不各自重算。 */
    @Inject(method = "render", at = @At("HEAD"))
    private void mahjongcraft$beginHandDisplayFrame(float tickDelta, long startTime, boolean tick, CallbackInfo callbackInfo) {
        ClientHandTileLayoutBridge.beginFrame(minecraft, tickDelta);
    }

    /** 原版完成選取後，以本人實際看到的牌張位置修正命中。 */
    @Inject(method = "pick", at = @At("TAIL"))
    private void mahjongcraft$adjustHandTileTarget(float tickDelta, CallbackInfo callbackInfo) {
        HitResult adjusted = ClientHandTileLayoutBridge.adjustTarget(
            minecraft,
            minecraft.hitResult,
            tickDelta
        );
        if (adjusted != null) {
            minecraft.hitResult = adjusted;
            minecraft.crosshairPickEntity = adjusted instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        }
    }
}
