package com.doublemoon1119.mahjongcraft.example.fabric.furniture

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.block.ExampleBenchBlock
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.block.ExampleChairBlock
import com.doublemoon1119.mahjongcraft.example.minecraft.furniture.ExampleFurnitureIds
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState

/** 範例家具方塊與對應物品的集中註冊點。 */
object ExampleFurnitureBlocks {
    /** 範例方塊椅子；由 [register] 初始化。 */
    lateinit var chair: Block
        private set

    /** 每格一席的可延長長凳。 */
    lateinit var bench: Block
        private set

    /** 判斷方塊是否使用範例的方塊座位服務。 */
    fun isSeat(state: BlockState): Boolean = isRegistered() && (state.`is`(chair) || state.`is`(bench))

    /** 確認方塊座位是否已完成原生註冊。 */
    fun isRegistered(): Boolean = ::chair.isInitialized && ::bench.isInitialized

    /** 註冊椅子、長凳與對應物品；座位保持實心，不被水流沖走。 */
    fun register() {
        chair = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation(ExampleFurnitureIds.CHAIR),
            ExampleChairBlock(
                settings = BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(1.5f).forceSolidOn(),
            ),
        )
        Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation(ExampleFurnitureIds.CHAIR),
            BlockItem(chair, Item.Properties()),
        )
        bench = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation(ExampleFurnitureIds.BENCH),
            ExampleBenchBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(1.5f).forceSolidOn()),
        )
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation(ExampleFurnitureIds.BENCH), BlockItem(bench, Item.Properties()))
    }
}
