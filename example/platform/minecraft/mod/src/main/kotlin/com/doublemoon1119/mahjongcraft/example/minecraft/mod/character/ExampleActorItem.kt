package com.doublemoon1119.mahjongcraft.example.minecraft.mod.character

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.ExampleFurnitureBlocks
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla.ExampleTooltipItem
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleCharacterMessageKeys
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext

/**
 * 在地面或既有範例座位建立人偶，不傳送或指定房間。
 *
 * @param settings 原生物品設定。
 */
class ExampleActorItem(settings: Properties) : ExampleTooltipItem(settings) {
    /** 座位使用命中本格，其餘使用一般方塊外側。權限及耗材由建立服務核對。 */
    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        if (context.level.isClientSide) return InteractionResult.SUCCESS
        val world = context.level as? ServerLevel ?: return InteractionResult.FAIL
        val hit = context.clickedPos
        val pos = if (ExampleFurnitureBlocks.isSeat(world.getBlockState(hit))) hit else hit.relative(context.clickedFace)
        return if (ExampleActorPlacement.placeAtBlock(
                world = world,
                player = player,
                stack = context.itemInHand,
                pos = pos,
            )
        ) {
            InteractionResult.CONSUME
        } else {
            InteractionResult.FAIL
        }
    }

    /** 直接說明取得後的使用方式與房主限制。 */
    override fun addTooltipLines(stack: ItemStack, lines: MutableList<Component>, flag: TooltipFlag) {
        lines += Component.translatable(ExampleCharacterMessageKeys.ITEM_HINT)
        lines += Component.translatable(ExampleCharacterMessageKeys.OWNER_HINT)
    }
}
