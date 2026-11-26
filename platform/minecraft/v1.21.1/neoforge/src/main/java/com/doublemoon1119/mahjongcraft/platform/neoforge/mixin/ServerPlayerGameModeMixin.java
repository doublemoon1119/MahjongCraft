package com.doublemoon1119.mahjongcraft.platform.neoforge.mixin;

import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玩家破壞的方塊確實被移除之後，發出玩家破壞方塊之後的事件。
 *
 * <p>NeoForge 在玩家破壞方塊時以自己加入的 {@code removeBlock} 移除方塊，傳入移除前的方塊狀態，回傳值表示是否真的移除。</p>
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    /** 玩家所在的世界。 */
    @Shadow protected ServerLevel level;

    /** 破壞方塊的玩家。 */
    @Shadow @Final protected ServerPlayer player;

    /** 移除前的方塊實體；只在移除方塊的期間有值，沒有方塊實體時為 null。 */
    @Unique private BlockEntity mahjongcraft$removedBlockEntity;

    /** 記下移除前的方塊實體。 */
    @Inject(method = "removeBlock", at = @At("HEAD"))
    private void mahjongcraft$captureRemovedBlockEntity(
        BlockPos pos,
        BlockState state,
        boolean canHarvest,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        mahjongcraft$removedBlockEntity = level.getBlockEntity(pos);
    }

    /** 方塊確實被移除時通知。 */
    @Inject(method = "removeBlock", at = @At("RETURN"))
    private void mahjongcraft$fireAfterPlayerBreakBlock(
        BlockPos pos,
        BlockState state,
        boolean canHarvest,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        BlockEntity blockEntity = mahjongcraft$removedBlockEntity;
        mahjongcraft$removedBlockEntity = null;
        if (callbackInfo.getReturnValueZ()) {
            NeoForgeServerEvents.fireAfterPlayerBreakBlock(level, player, pos, state, blockEntity);
        }
    }
}
