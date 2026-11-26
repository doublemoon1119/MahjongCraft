package com.doublemoon1119.mahjongcraft.platform.forge.mixin;

import com.doublemoon1119.mahjongcraft.platform.forge.loader.ForgeServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 伺服器世界的區塊記錄方塊實體後，發出方塊實體載入事件。 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    /** 區塊所在的世界。 */
    @Shadow @Final Level level;

    /** 區塊把方塊實體放進自己的方塊實體表之後通知；已有的同位置方塊實體被取代時也算載入。 */
    @Inject(
        method = "setBlockEntity",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            shift = At.Shift.AFTER
        )
    )
    private void mahjongcraft$fireBlockEntityLoad(BlockEntity blockEntity, CallbackInfo callbackInfo) {
        if (level instanceof ServerLevel serverLevel) {
            ForgeServerEvents.fireBlockEntityLoad(blockEntity, serverLevel);
        }
    }
}
