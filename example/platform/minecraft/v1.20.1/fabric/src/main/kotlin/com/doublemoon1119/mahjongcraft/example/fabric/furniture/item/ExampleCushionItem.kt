package com.doublemoon1119.mahjongcraft.example.fabric.furniture.item

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleCushionPlacement
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext

/**
 * 將坐墊物品放置成可保存的坐墊 entity。
 *
 * @param settings 原版物品設定。
 */
class ExampleCushionItem(
    settings: Properties,
) : Item(settings) {
    /** 在命中的平坦頂面放置坐墊，按表面高度定位，成功生成後才消耗物品。 */
    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        if (context.clickedFace != Direction.UP || !player.abilities.mayBuild) return InteractionResult.FAIL
        if (context.level.isClientSide) return InteractionResult.SUCCESS
        val world = context.level as? ServerLevel ?: return InteractionResult.FAIL
        val position = ExampleCushionPlacement.positionAt(context.clickedPos, context.clickLocation.y)
        val pos = BlockPos.containing(position)
        if (!world.chunkSource.hasChunk(pos.x shr 4, pos.z shr 4)) return InteractionResult.FAIL
        if (!world.mayInteract(player, context.clickedPos) || !world.mayInteract(player, pos)) return InteractionResult.FAIL
        if (!player.mayInteract(world, pos)) return InteractionResult.FAIL
        return if (
            ExampleCushionPlacement.place(
                world = world,
                player = player as ServerPlayer,
                stack = context.itemInHand,
                position = position,
                yaw = context.rotation,
            )
        ) {
            InteractionResult.CONSUME
        } else {
            InteractionResult.FAIL
        }
    }
}
