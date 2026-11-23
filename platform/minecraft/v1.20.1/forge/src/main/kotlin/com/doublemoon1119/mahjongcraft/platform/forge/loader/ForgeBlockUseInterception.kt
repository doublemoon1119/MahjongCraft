package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.BlockUseInterception
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.common.ForgeHooks
import net.minecraftforge.eventbus.api.Event

/** 以 Forge 的右鍵方塊事件實作 [BlockUseInterception]；事件沒有被取消、也沒有拒絕使用方塊才算允許。 */
object ForgeBlockUseInterception : BlockUseInterception {
    override fun allows(player: ServerPlayer, world: ServerLevel, hand: InteractionHand, hit: BlockHitResult): Boolean {
        val event = ForgeHooks.onRightClickBlock(player, hand, hit.blockPos, hit)
        return !event.isCanceled && event.useBlock != Event.Result.DENY
    }
}
