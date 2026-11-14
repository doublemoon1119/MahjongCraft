package com.doublemoon1119.mahjongcraft.example.fabric.character

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureBlocks
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleCharacterMessageKeys
import net.minecraft.client.item.TooltipContext
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.world.World

/**
 * 在地面或既有範例座位建立人偶，不傳送或指定房間。
 *
 * @param settings 原生物品設定。
 */
class ExampleActorItem(settings: Settings) : Item(settings) {
    /** 座位使用命中本格，其餘使用一般方塊外側；權限及耗材由建立服務核對。 */
    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val player = context.player ?: return ActionResult.PASS
        if (context.world.isClient) return ActionResult.SUCCESS
        val world = context.world as? ServerWorld ?: return ActionResult.FAIL
        val hit = context.blockPos
        val pos = if (ExampleFurnitureBlocks.isSeat(world.getBlockState(hit))) hit else hit.offset(context.side)
        return if (ExampleActorPlacement.placeAtBlock(
                world = world,
                player = player,
                stack = context.stack,
                pos = pos,
            )
        ) {
            ActionResult.CONSUME
        } else {
            ActionResult.FAIL
        }
    }

    /** 直接說明取得後的使用方式與房主限制。 */
    override fun appendTooltip(
        stack: ItemStack,
        world: World?,
        tooltip: MutableList<Text>,
        context: TooltipContext,
    ) {
        tooltip += Text.translatable(ExampleCharacterMessageKeys.ITEM_HINT)
        tooltip += Text.translatable(ExampleCharacterMessageKeys.OWNER_HINT)
    }
}
