package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.BlockUseInterception
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult

/** 以 Fabric 的 [UseBlockCallback] 實作 [BlockUseInterception]；回呼回傳 [InteractionResult.PASS] 才算允許。 */
object FabricBlockUseInterception : BlockUseInterception {
    override fun allows(player: ServerPlayer, world: ServerLevel, hand: InteractionHand, hit: BlockHitResult): Boolean = UseBlockCallback.EVENT.invoker().interact(player, world, hand, hit) == InteractionResult.PASS
}
