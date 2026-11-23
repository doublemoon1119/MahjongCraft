package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.mixin;

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.AutotestRuntime;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 自動測試期間不播放聲音，只記錄播放要求；不改動音量設定。 */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    /** 記錄並攔下立即播放的聲音。 */
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void mahjongcraft$recordInsteadOfPlay(SoundInstance sound, CallbackInfo callbackInfo) {
        if (AutotestRuntime.isActive()) {
            AutotestRuntime.recordSound(sound.getLocation().toString());
            callbackInfo.cancel();
        }
    }

    /** 記錄並攔下延後播放的聲音。 */
    @Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
    private void mahjongcraft$recordInsteadOfPlayDelayed(SoundInstance sound, int delay, CallbackInfo callbackInfo) {
        if (AutotestRuntime.isActive()) {
            AutotestRuntime.recordSound(sound.getLocation().toString());
            callbackInfo.cancel();
        }
    }
}
