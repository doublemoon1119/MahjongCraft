package com.doublemoon1119.mahjongcraft.example.fabric.furniture

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.block.ExampleChairBlock
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
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

    /** 確認範例椅子方塊是否已完成原生註冊。 */
    fun isRegistered(): Boolean = ::chair.isInitialized

    /** 註冊範例椅子方塊與 BlockItem。 */
    fun register() {
        chair = Registry.register(
            Registries.BLOCK,
            Identifier(ExampleFurnitureIds.CHAIR),
            ExampleChairBlock(
                AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).strength(1.5f),
            ),
        )
        Registry.register(
            Registries.ITEM,
            Identifier(ExampleFurnitureIds.CHAIR),
            BlockItem(chair, Item.Settings()),
        )
    }
}
