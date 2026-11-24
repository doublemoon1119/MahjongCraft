package com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.table

import com.doublemoon1119.mahjongcraft.example.minecraft.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.table.ExampleFlatTableDesign
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleBlockEntityTypes
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleRegistrar
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla.copyBlockProperties
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla.resourceId
import net.minecraft.core.registries.Registries
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
    /** 三種範例麻將桌共用的方塊實體型別；寫入登記表後可用。 */
    lateinit var blockEntityType: BlockEntityType<ExampleTableBlockEntity>
        private set

    /**
     * 登記三種範例麻將桌、對應物品與共用方塊實體型別。
     *
     * 桌子強制視為實心，避免依形狀被判定為不實心而遭水流沖掉；推不動，避免多格結構被活塞拆散。
     *
     * @param registrar 載入器的登記方式。
     */
    fun register(registrar: ExampleRegistrar) {
        val tables = mutableListOf<Block>()
        registerTable(registrar, ExampleFurnitureIds.ONE_BLOCK_TABLE, tables) { ExampleOneBlockTableBlock(tableSettings(Blocks.OAK_PLANKS)) }
        registerTable(registrar, ExampleFurnitureIds.LOW_TABLE, tables) {
            ExampleFlatTableBlock(tableSettings(Blocks.SPRUCE_PLANKS), ExampleFlatTableDesign.LOW)
        }
        registerTable(registrar, ExampleFurnitureIds.ROUND_TABLE, tables) {
            ExampleFlatTableBlock(tableSettings(Blocks.DARK_OAK_PLANKS), ExampleFlatTableDesign.ROUND)
        }
        registrar.register(
            registry = Registries.BLOCK_ENTITY_TYPE,
            id = resourceId(ExampleFurnitureIds.TABLE_BLOCK_ENTITY),
            factory = { ExampleBlockEntityTypes.create(::ExampleTableBlockEntity, *tables.toTypedArray()) },
            onRegistered = { blockEntityType = it },
        )
    }

    /**
     * 建立桌子共用的方塊設定。
     *
     * @param baseBlock 提供材質設定基礎的原版方塊。
     * @return 桌子的方塊設定。
     */
    private fun tableSettings(baseBlock: Block): BlockBehaviour.Properties = copyBlockProperties(baseBlock)
        .strength(2.5f)
        .pushReaction(PushReaction.BLOCK)
        .forceSolidOn()

    /**
     * 登記一種桌子方塊與它的物品。
     *
     * @param registrar 載入器的登記方式。
     * @param id 方塊與物品共用的識別碼。
     * @param tables 收集寫入後的桌子方塊，供方塊實體型別使用。
     * @param factory 建立桌子方塊的函式。
     */
    private fun registerTable(registrar: ExampleRegistrar, id: String, tables: MutableList<Block>, factory: () -> Block) {
        lateinit var table: Block
        registrar.register(
            registry = Registries.BLOCK,
            id = resourceId(id),
            factory = factory,
            onRegistered = {
                table = it
                tables += it
            },
        )
        registrar.register(Registries.ITEM, resourceId(id), { BlockItem(table, Item.Properties()) })
    }
}
