package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.mixin;

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.AutotestRuntime;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 自動測試期間視窗一律視為作用中，失去焦點時不暫停遊戲。 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /** 視窗沒有焦點時仍回報作用中。 */
    @Inject(method = "isWindowActive", at = @At("HEAD"), cancellable = true)
    private void mahjongcraft$alwaysActive(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (AutotestRuntime.isActive()) callbackInfo.setReturnValue(true);
    }
}
