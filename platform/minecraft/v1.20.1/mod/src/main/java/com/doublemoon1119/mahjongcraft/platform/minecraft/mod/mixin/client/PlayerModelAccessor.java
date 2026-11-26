package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.mixin.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 讓 {@link CapeLayerMixin} 以指定不透明度畫出原版玩家模型的披風。 */
@Mixin(PlayerModel.class)
public interface PlayerModelAccessor {
    /**
     * 原版玩家模型的披風部位。
     *
     * @return 披風部位。
     */
    @Accessor("cloak")
    ModelPart mahjongcraft$cloak();
}
