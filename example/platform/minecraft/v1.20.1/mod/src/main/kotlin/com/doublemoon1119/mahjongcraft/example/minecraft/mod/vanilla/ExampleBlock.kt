package com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * 範例一般方塊的基底。
 *
 * - 右鍵互動寫在新版 Minecraft 名稱的 [useWithoutItem]，這個版本的原版互動方法由這個類別轉接。
 * - 與新版相同，只處理主手的右鍵。
 * - 玩家手上的物品要自己處理右鍵時，[useWithoutItem] 回傳 PASS 讓物品接手。
 *
 * @param properties 原版方塊設定。
 */
abstract class ExampleBlock(properties: Properties) : Block(properties) {
    /**
     * 玩家以主手右鍵這個方塊時的處理。預設不處理。
     *
     * @param state 方塊狀態。
     * @param world 所在世界。
     * @param pos 方塊位置。
     * @param player 右鍵的玩家。
     * @param hit 命中的位置與面。
     * @return 互動結果。
     */
    protected open fun useWithoutItem(state: BlockState, world: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult = InteractionResult.PASS

    @Suppress("OVERRIDE_DEPRECATION")
    final override fun use(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): InteractionResult = if (hand == InteractionHand.MAIN_HAND) useWithoutItem(state, world, pos, player, hit) else InteractionResult.PASS
}
