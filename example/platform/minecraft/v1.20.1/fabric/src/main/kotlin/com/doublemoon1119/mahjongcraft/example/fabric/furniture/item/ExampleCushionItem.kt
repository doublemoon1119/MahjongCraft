package com.doublemoon1119.mahjongcraft.example.fabric.furniture.item

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleCushionPlacement
import net.minecraft.item.Item
import net.minecraft.item.ItemUsageContext
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

/**
 * 將坐墊物品放置成可保存的坐墊 entity。
 *
 * @param settings 原版物品設定。
 */
class ExampleCushionItem(
    settings: Settings,
) : Item(settings) {
    /** 在命中的平坦頂面放置坐墊，按表面高度定位，成功生成後才消耗物品。 */
    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val player = context.player ?: return ActionResult.PASS
        if (context.side != Direction.UP || !player.abilities.allowModifyWorld) return ActionResult.FAIL
        if (context.world.isClient) return ActionResult.SUCCESS
        val world = context.world as? ServerWorld ?: return ActionResult.FAIL
        val position = ExampleCushionPlacement.positionAt(context.blockPos, context.hitPos.y)
        val pos = BlockPos.ofFloored(position)
        if (!world.chunkManager.isChunkLoaded(pos.x shr 4, pos.z shr 4)) return ActionResult.FAIL
        if (!world.canPlayerModifyAt(player, context.blockPos) || !world.canPlayerModifyAt(player, pos)) return ActionResult.FAIL
        if (!player.canModifyAt(world, pos)) return ActionResult.FAIL
        return if (
            ExampleCushionPlacement.place(
                world = world,
                player = player as ServerPlayerEntity,
                stack = context.stack,
                position = position,
                yaw = context.playerYaw,
            )
        ) {
            ActionResult.CONSUME
        } else {
            ActionResult.FAIL
        }
    }
}
