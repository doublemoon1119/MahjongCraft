package com.doublemoon1119.mahjongcraft.example.fabric.furniture.table

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureIds
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.PushReaction

/**
 * 三種範例麻將桌與共用方塊實體型別的註冊點。
 *
 * MahjongCraft 不需要另外登記桌子：方塊實作 `MahjongTableStructureBlock`、方塊實體實作 `MahjongTableController` 即可。
 */
object ExampleTables {
    /** 三種範例麻將桌共用的方塊實體型別；由 [register] 初始化。 */
    lateinit var blockEntityType: BlockEntityType<ExampleTableBlockEntity>
        private set

    /**
     * 註冊三種範例麻將桌、對應物品與共用方塊實體型別。
     *
     * 桌子強制視為實心，避免依形狀被判定為不實心而遭水流沖掉；推不動，避免多格結構被活塞拆散。
     */
    fun register() {
        val oneBlockTable = registerTable(ExampleFurnitureIds.ONE_BLOCK_TABLE, ExampleOneBlockTableBlock(tableSettings(Blocks.OAK_PLANKS)))
        val lowTable = registerTable(ExampleFurnitureIds.LOW_TABLE, ExampleFlatTableBlock(tableSettings(Blocks.SPRUCE_PLANKS), ExampleFlatTableDesign.LOW))
        val roundTable = registerTable(ExampleFurnitureIds.ROUND_TABLE, ExampleFlatTableBlock(tableSettings(Blocks.DARK_OAK_PLANKS), ExampleFlatTableDesign.ROUND))
        blockEntityType = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation(ExampleFurnitureIds.TABLE_BLOCK_ENTITY),
            FabricBlockEntityTypeBuilder.create(::ExampleTableBlockEntity, oneBlockTable, lowTable, roundTable).build(),
        )
    }

    /**
     * 建立桌子共用的方塊設定。
     *
     * @param baseBlock 提供材質設定基礎的原版方塊。
     * @return 桌子的方塊設定。
     */
    private fun tableSettings(baseBlock: Block): BlockBehaviour.Properties = BlockBehaviour.Properties.copy(baseBlock)
        .strength(2.5f)
        .pushReaction(PushReaction.BLOCK)
        .forceSolidOn()

    /**
     * 註冊一種桌子方塊與它的物品。
     *
     * @param id 方塊與物品共用的識別碼。
     * @param block 要註冊的方塊。
     * @return 已註冊的方塊。
     */
    private fun registerTable(id: String, block: Block): Block {
        val registered = Registry.register(BuiltInRegistries.BLOCK, ResourceLocation(id), block)
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation(id), BlockItem(registered, Item.Properties()))
        return registered
    }
}
