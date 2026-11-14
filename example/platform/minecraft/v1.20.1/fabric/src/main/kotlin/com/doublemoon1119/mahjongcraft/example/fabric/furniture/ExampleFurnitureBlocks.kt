package com.doublemoon1119.mahjongcraft.example.fabric.furniture

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.block.ExampleBenchBlock
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.block.ExampleChairBlock
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

/** 範例家具方塊與對應物品的集中註冊點。 */
object ExampleFurnitureBlocks {
    /** 範例方塊椅子；由 [register] 初始化。 */
    lateinit var chair: Block
        private set

    /** 每格一席的可延長長凳。 */
    lateinit var bench: Block
        private set

    /** 判斷方塊是否使用範例的方塊座位服務。 */
    fun isSeat(state: BlockState): Boolean = isRegistered() && (state.isOf(chair) || state.isOf(bench))

    /** 確認方塊座位是否已完成原生註冊。 */
    fun isRegistered(): Boolean = ::chair.isInitialized && ::bench.isInitialized

    /** 註冊椅子、長凳與對應物品；座位保持實心，不被水流沖走。 */
    fun register() {
        chair = Registry.register(
            Registries.BLOCK,
            Identifier(ExampleFurnitureIds.CHAIR),
            ExampleChairBlock(
                settings = AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).strength(1.5f).solid(),
            ),
        )
        Registry.register(
            Registries.ITEM,
            Identifier(ExampleFurnitureIds.CHAIR),
            BlockItem(chair, Item.Settings()),
        )
        bench = Registry.register(
            Registries.BLOCK,
            Identifier(ExampleFurnitureIds.BENCH),
            ExampleBenchBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).strength(1.5f).solid()),
        )
        Registry.register(Registries.ITEM, Identifier(ExampleFurnitureIds.BENCH), BlockItem(bench, Item.Settings()))
    }
}
