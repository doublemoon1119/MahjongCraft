package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * 本 mod 一般方塊的基底。
 *
 * - 右鍵互動以新版 Minecraft 的 [useWithoutItem] 撰寫。
 * - 這個版本的原版互動方法，與版本專屬的要求（例如方塊 codec），由這個類別處理。
 * - 與新版相同，只處理主手的右鍵。
 *
 * @param properties 原版方塊設定。
 */
abstract class MahjongBlock(properties: Properties) : Block(properties) {
    /**
     * 玩家以主手右鍵這個方塊時的處理，名稱與新版 Minecraft 相同。預設不處理。
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
