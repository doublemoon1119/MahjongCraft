package com.doublemoon1119.mahjongcraft.platform.forge.mixin;

import com.doublemoon1119.mahjongcraft.platform.forge.loader.ForgeServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 伺服器世界開始追蹤實體之後，發出實體加入世界的事件。
 *
 * <p>Forge 的實體加入事件在實體放進世界之前發出，這裡的時間點則是實體已在世界中。</p>
 */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class ServerLevelEntityCallbacksMixin {
    /** 原版開始追蹤實體的處理結束時通知。 */
    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void mahjongcraft$fireEntityLoad(Entity entity, CallbackInfo callbackInfo) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            ForgeServerEvents.fireEntityLoad(entity, serverLevel);
        }
    }
}
