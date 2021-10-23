package doublemoon.mahjongcraft.mixin;

import doublemoon.mahjongcraft.event.PlayerLoggedOutEventKt;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {

    @Inject(method = "remove",at = @At("HEAD"))
    public void remove(ServerPlayerEntity player, CallbackInfo ci){
        PlayerLoggedOutEventKt.onPlayerLoggedOut(player);
    }

}
