package com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.seating

import com.doublemoon1119.mahjongcraft.example.minecraft.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.ExampleFurniture
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.block.ExampleBenchPlacement
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla.resourceId
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.seating.SeatPlacementContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.seating.SeatPlacementDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.seating.SeatPlacementProvider
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

/** 範例的方塊椅子、長凳及實體坐墊共用本體的座位物品放置入口。 */
internal object ExampleSeatPlacements {
    /**
     * 建立初始化用描述，不讀取原生物品 registry 或世界。
     *
     * @return 三種座位物品的放置能力。
     */
    fun definitions(): List<SeatPlacementDefinition> = listOf(
        SeatPlacementDefinition(ExampleFurnitureIds.CHAIR, Chair),
        SeatPlacementDefinition(ExampleFurnitureIds.CUSHION, Cushion),
        SeatPlacementDefinition(ExampleFurnitureIds.BENCH, Bench),
    )

    /** 方塊椅子沿用 BlockItem 的放置與扣物品流程。 */
    private object Chair : SeatPlacementProvider {
        override fun supports(stack: ItemStack): Boolean = stack.`is`(ExampleFurniture.chairItem)

        override fun place(context: SeatPlacementContext): Boolean = context.placeBlockItem(context.request.seat.yaw)
    }

    /** 放置視角與開口相反，讓成功放置的長凳朝向指定席位。 */
    private object Bench : SeatPlacementProvider {
        override fun supports(stack: ItemStack): Boolean = stack.`is`(BuiltInRegistries.ITEM.get(resourceId(ExampleFurnitureIds.BENCH)))

        override fun place(context: SeatPlacementContext): Boolean = context.placeBlockItem(ExampleBenchPlacement.playerYaw(context.request.seat.yaw))
    }

    /** 坐墊使用一般放置時相同的支撐、空間與耗材邏輯。 */
    private object Cushion : SeatPlacementProvider {
        override fun supports(stack: ItemStack): Boolean = stack.`is`(ExampleFurniture.cushionItem)

        override fun place(context: SeatPlacementContext): Boolean {
            if (!context.allowsBlockUse()) return false
            val seat = context.request.seat
            return ExampleCushionPlacement.place(
                world = context.world,
                player = context.player,
                stack = context.player.getItemInHand(context.hand),
                position = Vec3(seat.x, seat.y, seat.z),
                yaw = seat.yaw,
                ignoredActor = context.rider,
            )
        }
    }
}
