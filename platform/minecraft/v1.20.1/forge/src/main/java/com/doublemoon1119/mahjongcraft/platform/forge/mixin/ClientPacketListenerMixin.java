package com.doublemoon1119.mahjongcraft.platform.forge.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraftforge.client.ClientCommandHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 讓點擊聊天訊息執行的指令也先交給用戶端指令處理。
 *
 * <p>Forge 只在玩家輸入的指令送出前交給 {@link ClientCommandHandler}；點擊聊天訊息執行的指令改走 {@code sendUnsignedCommand}，
 * 會直接送到伺服器，伺服器不認得只在用戶端登記的指令。這裡補上同樣的處理：用戶端認得的指令在用戶端執行，其他照原本送出。</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    /** 用戶端處理了指令時不再送到伺服器，並回報已送出。 */
    @Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true)
    private void mahjongcraft$runClientCommand(String command, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (ClientCommandHandler.runCommand(command)) {
            callbackInfo.setReturnValue(true);
        }
    }
}
