package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.mixin;

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.AutotestRuntime;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 自動測試期間忽略鍵盤按鍵與文字輸入。 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    /** 忽略按鍵。 */
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void mahjongcraft$ignoreKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo callbackInfo) {
        if (AutotestRuntime.isActive()) callbackInfo.cancel();
    }

    /** 忽略文字輸入。 */
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void mahjongcraft$ignoreCharacter(long window, int codePoint, int modifiers, CallbackInfo callbackInfo) {
        if (AutotestRuntime.isActive()) callbackInfo.cancel();
    }
}
