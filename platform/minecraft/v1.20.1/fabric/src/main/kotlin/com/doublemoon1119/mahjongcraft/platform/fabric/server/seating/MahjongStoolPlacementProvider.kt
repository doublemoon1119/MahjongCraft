package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatPlacementContext
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatPlacementProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongStoolBlock
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack

/** 辨識各款內建凳子物品，以正常 BlockItem 流程放置。 */
internal object MahjongStoolPlacementProvider : FabricSeatPlacementProvider {
    override fun supports(stack: ItemStack): Boolean = (stack.item as? BlockItem)?.block is MahjongStoolBlock

    /** 凳子使用與放置者相反的 facing，因此將放置視角轉向桌外。 */
    override fun place(context: FabricSeatPlacementContext): Boolean = context.placeBlockItem(context.request.seat.yaw + 180f)
}
