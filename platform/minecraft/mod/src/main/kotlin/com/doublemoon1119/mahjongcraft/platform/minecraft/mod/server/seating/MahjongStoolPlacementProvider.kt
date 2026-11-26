package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.seating.SeatPlacementContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.seating.SeatPlacementProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongStoolBlock
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack

/** 辨識各款內建凳子物品，以正常 BlockItem 流程放置。 */
internal object MahjongStoolPlacementProvider : SeatPlacementProvider {
    override fun supports(stack: ItemStack): Boolean = (stack.item as? BlockItem)?.block is MahjongStoolBlock

    /** 凳子使用與放置者相反的 facing，因此將放置視角轉向桌外。 */
    override fun place(context: SeatPlacementContext): Boolean = context.placeBlockItem(context.request.seat.yaw + 180f)
}
