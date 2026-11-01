package com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurniture
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatPlacementContext
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatPlacementDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatPlacementProvider
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Vec3d

/** 範例的方塊椅子與實體坐墊共用本體的座位物品放置入口。 */
internal object ExampleSeatPlacements {
    /**
     * 建立初始化用描述，不讀取原生物品 registry 或世界。
     *
     * @return 兩種座位物品的放置能力。
     */
    fun definitions(): List<FabricSeatPlacementDefinition> = listOf(
        FabricSeatPlacementDefinition(ExampleFurnitureIds.CHAIR, Chair),
        FabricSeatPlacementDefinition(ExampleFurnitureIds.CUSHION, Cushion),
    )

    /** 方塊椅子沿用 BlockItem 的放置與扣物品流程。 */
    private object Chair : FabricSeatPlacementProvider {
        override fun supports(stack: ItemStack): Boolean = stack.isOf(ExampleFurniture.chairItem)

        override fun place(context: FabricSeatPlacementContext): Boolean = context.placeBlockItem(context.request.seat.yaw)
    }

    /** 坐墊使用一般放置時相同的支撐、空間與耗材邏輯。 */
    private object Cushion : FabricSeatPlacementProvider {
        override fun supports(stack: ItemStack): Boolean = stack.isOf(ExampleFurniture.cushionItem)

        override fun place(context: FabricSeatPlacementContext): Boolean {
            if (!context.allowsBlockUse()) return false
            val seat = context.request.seat
            return ExampleCushionPlacement.place(
                world = context.world,
                player = context.player,
                stack = context.player.getStackInHand(context.hand),
                position = Vec3d(seat.x, seat.y, seat.z),
                yaw = seat.yaw,
                ignoredActor = context.rider,
            )
        }
    }
}
