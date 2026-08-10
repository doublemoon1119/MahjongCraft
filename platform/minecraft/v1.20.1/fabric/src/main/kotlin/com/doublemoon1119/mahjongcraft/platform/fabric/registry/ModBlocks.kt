package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

/** MahjongCraft Fabric 方塊與方塊實體的集中註冊點。 */
object ModBlocks {
    /** 最小可互動麻將桌方塊；由 [register] 初始化。 */
    lateinit var mahjongTable: Block
        private set

    /** 麻將桌方塊實體型別；由 [register] 初始化。 */
    lateinit var mahjongTableBlockEntity: BlockEntityType<MahjongTableBlockEntity>
        private set

    /** 註冊麻將桌方塊、對應物品與方塊實體型別。 */
    fun register(roomService: MahjongTableRoomService) {
        val id = Identifier(MinecraftModMetadata.MOD_ID, "mahjong_table")
        mahjongTable = Registry.register(
            Registries.BLOCK,
            id,
            MahjongTableBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).strength(2.5f), roomService),
        )
        Registry.register(Registries.ITEM, id, BlockItem(mahjongTable, Item.Settings()))
        mahjongTableBlockEntity = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            id,
            FabricBlockEntityTypeBuilder.create(::MahjongTableBlockEntity, mahjongTable).build(),
        )
    }
}
