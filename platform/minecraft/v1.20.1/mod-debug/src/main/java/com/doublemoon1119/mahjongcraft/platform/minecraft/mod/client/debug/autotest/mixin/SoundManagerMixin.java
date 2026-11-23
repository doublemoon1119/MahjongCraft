package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.mixin;

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.AutotestRuntime;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自動測試期間記錄每次聲音播放要求；聲音照常載入與播放。
 *
 * <p>自動測試的開發執行讓音訊庫使用不連接任何裝置的空輸出，因此聽不到聲音，也不改動音量設定。</p>
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    /** 記錄立即播放的聲音。 */
    @Inject(method = "play", at = @At("HEAD"))
    private void mahjongcraft$recordPlay(SoundInstance sound, CallbackInfo callbackInfo) {
        if (AutotestRuntime.isActive()) AutotestRuntime.recordSound(sound.getLocation().toString());
    }

    /** 記錄延後播放的聲音。 */
    @Inject(method = "playDelayed", at = @At("HEAD"))
    private void mahjongcraft$recordPlayDelayed(SoundInstance sound, int delay, CallbackInfo callbackInfo) {
        if (AutotestRuntime.isActive()) AutotestRuntime.recordSound(sound.getLocation().toString());
    }
}
