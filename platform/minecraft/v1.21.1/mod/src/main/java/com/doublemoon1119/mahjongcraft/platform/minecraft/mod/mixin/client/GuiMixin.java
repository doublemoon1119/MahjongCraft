package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.mixin.client;

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.HudBeforeChatHook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在原版聊天欄之前通知 {@link HudBeforeChatHook}；原版沒有讓 mod 把畫面元素排在聊天欄之前的方式。
 *
 * <p>在原版畫聊天欄的圖層開始時發出通知，不修改或取消任何原版聊天行為；聊天畫面開啟時聊天欄由聊天畫面自己畫，這個圖層
 * 仍會先執行，因此每幀都會通知。以自己的畫面分層取代原版 {@code Gui.render} 的載入器
 * 不會執行這裡，由該載入器提供同一個時間點。</p>
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderChat", at = @At("HEAD"))
    private void mahjongcraft$renderPassiveHudBeforeChat(
        GuiGraphics context,
        DeltaTracker deltaTracker,
        CallbackInfo callbackInfo
    ) {
        HudBeforeChatHook.fire(context);
    }
}
