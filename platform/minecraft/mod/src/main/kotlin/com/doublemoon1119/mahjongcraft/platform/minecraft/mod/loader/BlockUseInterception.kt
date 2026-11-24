package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult

/**
 * 在本 mod 代替玩家使用方塊前，先經過載入器的「玩家右鍵方塊」事件，讓其他模組有機會拒絕。
 *
 * 原版沒有這個事件，由載入器提供。
 */
fun interface BlockUseInterception {
    /**
     * 詢問其他模組是否允許這次方塊使用。
     *
     * @param player 執行互動的玩家。
     * @param world 目標世界。
     * @param hand 觸發互動的手。
     * @param hit 被使用的方塊面。
     * @return 沒有模組攔截時為 true。
     */
    fun allows(player: ServerPlayer, world: ServerLevel, hand: InteractionHand, hit: BlockHitResult): Boolean
}
