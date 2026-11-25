package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.mixin;

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.AutotestRuntime;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 自動測試期間忽略滑鼠的點擊、捲動與移動，也不鎖定游標。 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    /** 忽略滑鼠按鍵。 */
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void mahjongcraft$ignorePress(long window, int button, int action, int modifiers, CallbackInfo callbackInfo) {
        if (AutotestRuntime.isActive()) callbackInfo.cancel();
    }

    /** 忽略滑鼠滾輪。 */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void mahjongcraft$ignoreScroll(long window, double horizontal, double vertical, CallbackInfo callbackInfo) {
        if (AutotestRuntime.isActive()) callbackInfo.cancel();
    }

    /** 忽略滑鼠移動。 */
    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void mahjongcraft$ignoreMove(long window, double x, double y, CallbackInfo callbackInfo) {
        if (AutotestRuntime.isActive()) callbackInfo.cancel();
    }

    /** 不把游標鎖在遊戲視窗中。 */
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void mahjongcraft$keepCursorFree(CallbackInfo callbackInfo) {
        if (AutotestRuntime.isActive()) callbackInfo.cancel();
    }
}
