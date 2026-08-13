package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongStoolBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongStoolDesign
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableDesign
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.MapColor
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.block.piston.PistonBehavior
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.BlockSoundGroup
import net.minecraft.util.Identifier

/** MahjongCraft Fabric 方塊與方塊實體的集中註冊點。 */
object ModBlocks {
    /** 統一木製外觀、深綠桌面與四腳碰撞 profile 的麻將桌；由 [register] 初始化。 */
    lateinit var woodenMahjongTable: Block
        private set

    /** 統一混凝土外觀、深綠桌面與中央柱碰撞 profile 的麻將桌；由 [register] 初始化。 */
    lateinit var concreteMahjongTable: Block
        private set

    /** 狹長剝皮橡木外觀、外張斜腳與完整支架碰撞的麻將凳；由 [register] 初始化。 */
    lateinit var woodenMahjongStool: Block
        private set

    /** 亮紅塑膠外觀、鏤空側面與完整外框碰撞的麻將凳；由 [register] 初始化。 */
    lateinit var plasticMahjongStool: Block
        private set

    /** 麻將桌方塊實體型別；由 [register] 初始化。 */
    lateinit var mahjongTableBlockEntity: BlockEntityType<MahjongTableBlockEntity>
        private set

    /** 註冊麻將桌方塊、對應物品與方塊實體型別。 */
    fun register(
        roomService: MahjongTableRoomService,
        tableLifecycleService: FabricTableLifecycleService,
    ) {
        woodenMahjongTable = registerTable(
            path = "wooden_mahjong_table",
            design = MahjongTableDesign.FOUR_LEG,
            baseBlock = Blocks.OAK_PLANKS,
            roomService = roomService,
            tableLifecycleService = tableLifecycleService,
        )
        concreteMahjongTable = registerTable(
            path = "concrete_mahjong_table",
            design = MahjongTableDesign.PEDESTAL,
            baseBlock = Blocks.GRAY_CONCRETE,
            roomService = roomService,
            tableLifecycleService = tableLifecycleService,
        )
        woodenMahjongStool = registerStool(
            path = "wooden_mahjong_stool",
            design = MahjongStoolDesign.WOODEN,
            settings = AbstractBlock.Settings.create()
                .mapColor(MapColor.OAK_TAN)
                .strength(0.0f)
                .sounds(BlockSoundGroup.WOOD),
        )
        plasticMahjongStool = registerStool(
            path = "plastic_mahjong_stool",
            design = MahjongStoolDesign.PLASTIC,
            settings = AbstractBlock.Settings.create()
                .mapColor(MapColor.BRIGHT_RED)
                .strength(0.0f)
                .sounds(BlockSoundGroup.SCAFFOLDING),
        )
        val blockEntityId = Identifier(MinecraftModMetadata.MOD_ID, "mahjong_table")
        mahjongTableBlockEntity = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            blockEntityId,
            FabricBlockEntityTypeBuilder.create(
                ::MahjongTableBlockEntity,
                woodenMahjongTable,
                concreteMahjongTable,
            ).build(),
        )
    }

    /** 註冊具有指定 ID 與碰撞 profile 的麻將桌方塊及 BlockItem。 */
    private fun registerTable(
        path: String,
        design: MahjongTableDesign,
        baseBlock: Block,
        roomService: MahjongTableRoomService,
        tableLifecycleService: FabricTableLifecycleService,
    ): Block {
        val id = Identifier(MinecraftModMetadata.MOD_ID, path)
        val block = Registry.register(
            Registries.BLOCK,
            id,
            MahjongTableBlock(
                settings = AbstractBlock.Settings.copy(baseBlock)
                    .strength(2.5f)
                    .pistonBehavior(PistonBehavior.BLOCK),
                design = design,
                roomService = roomService,
                tableLifecycleService = tableLifecycleService,
            ),
        )
        Registry.register(Registries.ITEM, id, BlockItem(block, Item.Settings()))
        return block
    }

    /** 註冊具有指定固定款式的單方塊麻將凳及 BlockItem。 */
    private fun registerStool(
        path: String,
        design: MahjongStoolDesign,
        settings: AbstractBlock.Settings,
    ): Block {
        val id = Identifier(MinecraftModMetadata.MOD_ID, path)
        val block = Registry.register(
            Registries.BLOCK,
            id,
            MahjongStoolBlock(
                settings = settings,
                design = design,
            ),
        )
        Registry.register(Registries.ITEM, id, BlockItem(block, Item.Settings()))
        return block
    }
}
