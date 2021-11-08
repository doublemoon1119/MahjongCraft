package doublemoon.mahjongcraft.mixin;

import com.mojang.authlib.GameProfile;
import doublemoon.mahjongcraft.entity.SeatEntity;
import doublemoon.mahjongcraft.event.PlayerChangedDimensionEventKt;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin extends PlayerEntity {

    public ServerPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile profile) {
        super(world, pos, yaw, profile);
    }

    @Shadow
    public ServerPlayNetworkHandler networkHandler;

    @Shadow
    public abstract ServerWorld getServerWorld();

    @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
    public void onStopRiding(CallbackInfo ci) {
        Entity entity = this.getVehicle();
        if (entity instanceof SeatEntity) {
            double yPos = this.getY(); //先取得 y 座標, 不然等等就拿不到了
            super.stopRiding();
            Entity entity2 = this.getVehicle();
            if (entity2 != entity && this.networkHandler != null) {
                this.networkHandler.requestTeleport(
                        this.getX(),
                        yPos + ((SeatEntity) entity).getStopSitOffsetY(),
                        this.getZ(),
                        this.getYaw(),
                        this.getPitch()
                );
            }
            ci.cancel();
        }
    }


    @Inject(method = "teleport", at = @At("HEAD"))
    public void teleport(ServerWorld targetWorld, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        if (targetWorld != this.world) {
            PlayerChangedDimensionEventKt.onPlayerChangedDimension(this, this.getServerWorld().getRegistryKey(), targetWorld.getRegistryKey());
        }
    }

    @Inject(method = "moveToWorld", at = @At("HEAD"))
    public void moveToWorld(ServerWorld destination, CallbackInfoReturnable<Entity> cir) {
        ServerWorld serverWorld = this.getServerWorld();
        RegistryKey<World> registryKey = serverWorld.getRegistryKey();
        if (!(registryKey == World.END && destination.getRegistryKey() == World.OVERWORLD)) {
            PlayerChangedDimensionEventKt.onPlayerChangedDimension(this, registryKey, destination.getRegistryKey());
        }
    }
}

