package com.doublemoon1119.mahjongcraft.platform.neoforge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.BlockUseInterception
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.neoforge.common.CommonHooks
import net.neoforged.neoforge.common.util.TriState

/** 以 NeoForge 的右鍵方塊事件判斷其他模組是否攔下方塊使用。 */
object NeoForgeBlockUseInterception : BlockUseInterception {
    override fun allows(player: ServerPlayer, world: ServerLevel, hand: InteractionHand, hit: BlockHitResult): Boolean {
        val event = CommonHooks.onRightClickBlock(player, hand, hit.blockPos, hit)
        return !event.isCanceled && event.useBlock != TriState.FALSE
    }
}
