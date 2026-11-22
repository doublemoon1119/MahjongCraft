package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.mixin.client;

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.PlayerDecisionHudController;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在原版聊天欄之前繪製本 mod 的被動 HUD；原版沒有讓 mod 把畫面元素排在聊天欄之前的方式。
 *
 * <p>只在原版聊天欄繪製前轉交既有被動 HUD renderer，不修改或取消任何原版聊天行為。</p>
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/GuiGraphics;III)V",
            shift = At.Shift.BEFORE
        )
    )
    private void mahjongcraft$renderPassiveHudBeforeChat(
        GuiGraphics context,
        float tickDelta,
        CallbackInfo callbackInfo
    ) {
        PlayerDecisionHudController.renderPassiveHudBeforeChat(context);
    }
}
