package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongStoolBlock
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongStoolDesign
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongTableDesign
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.RegisteredEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.MahjongBlockEntityTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.MahjongStoolSeatService
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction

/** MahjongCraft 方塊與方塊實體的集中登記點；各項目在 [register] 登記完成後才能讀取。 */
object ModBlocks {
    /** 登記完成後可讀取 [woodenMahjongTable] 的項目。 */
    private lateinit var woodenMahjongTableEntry: RegisteredEntry<Block>

    /** 登記完成後可讀取 [concreteMahjongTable] 的項目。 */
    private lateinit var concreteMahjongTableEntry: RegisteredEntry<Block>

    /** 登記完成後可讀取 [woodenMahjongStool] 的項目。 */
    private lateinit var woodenMahjongStoolEntry: RegisteredEntry<Block>

    /** 登記完成後可讀取 [plasticMahjongStool] 的項目。 */
    private lateinit var plasticMahjongStoolEntry: RegisteredEntry<Block>

    /** 登記完成後可讀取 [mahjongTableBlockEntity] 的項目。 */
    private lateinit var mahjongTableBlockEntityEntry: RegisteredEntry<BlockEntityType<MahjongTableBlockEntity>>

    /** 統一木製外觀、深綠桌面與四腳碰撞 profile 的麻將桌。 */
    val woodenMahjongTable: Block
        get() = woodenMahjongTableEntry.get()

    /** 統一混凝土外觀、深綠桌面與中央柱碰撞 profile 的麻將桌。 */
    val concreteMahjongTable: Block
        get() = concreteMahjongTableEntry.get()

    /** 狹長剝皮橡木外觀、外張斜腳與完整支架碰撞的麻將凳。 */
    val woodenMahjongStool: Block
        get() = woodenMahjongStoolEntry.get()

    /** 亮紅塑膠外觀、鏤空側面與完整外框碰撞的麻將凳。 */
    val plasticMahjongStool: Block
        get() = plasticMahjongStoolEntry.get()

    /** 麻將桌方塊實體型別。 */
    val mahjongTableBlockEntity: BlockEntityType<MahjongTableBlockEntity>
        get() = mahjongTableBlockEntityEntry.get()

    /**
     * 登記麻將桌與麻將凳方塊、對應物品與方塊實體型別。
     *
     * 桌子與凳子都強制視為實心：細長或沒有碰撞的部件依形狀推算會被判定為不實心，水流會沖掉不實心的方塊。
     *
     * @param registrar 寫入登記表的方式。
     * @param seatService 麻將凳乘坐服務。
     */
    fun register(registrar: ModRegistrar, seatService: MahjongStoolSeatService) {
        woodenMahjongTableEntry = registrar.registerTable(
            path = "wooden_mahjong_table",
            design = MahjongTableDesign.FOUR_LEG,
            baseBlock = Blocks.OAK_PLANKS,
        )
        concreteMahjongTableEntry = registrar.registerTable(
            path = "concrete_mahjong_table",
            design = MahjongTableDesign.PEDESTAL,
            baseBlock = Blocks.GRAY_CONCRETE,
        )
        woodenMahjongStoolEntry = registrar.registerStool(
            path = "wooden_mahjong_stool",
            design = MahjongStoolDesign.WOODEN,
            settings = {
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.0f)
                    .sound(SoundType.WOOD)
                    .forceSolidOn()
            },
            seatService = seatService,
        )
        plasticMahjongStoolEntry = registrar.registerStool(
            path = "plastic_mahjong_stool",
            design = MahjongStoolDesign.PLASTIC,
            settings = {
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.FIRE)
                    .strength(0.0f)
                    .sound(SoundType.SCAFFOLDING)
                    .forceSolidOn()
            },
            seatService = seatService,
        )
        mahjongTableBlockEntityEntry = registrar.register(
            registry = Registries.BLOCK_ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_table"),
        ) {
            MahjongBlockEntityTypes.create(::MahjongTableBlockEntity, woodenMahjongTable, concreteMahjongTable)
        }
    }

    /**
     * 登記具有指定 ID 與碰撞 profile 的麻將桌方塊及 BlockItem。
     *
     * @param path 方塊 registry 路徑。
     * @param design 桌面外觀與碰撞設計。
     * @param baseBlock 提供材質設定基礎的原版方塊。
     * @return 登記完成後可讀取方塊的項目。
     */
    private fun ModRegistrar.registerTable(
        path: String,
        design: MahjongTableDesign,
        baseBlock: Block,
    ): RegisteredEntry<Block> = registerWithItem(path) {
        MahjongTableBlock(
            settings = BlockBehaviour.Properties.copy(baseBlock)
                .strength(2.5f)
                .pushReaction(PushReaction.BLOCK)
                .forceSolidOn(),
            design = design,
        )
    }

    /**
     * 登記具有指定固定款式的單方塊麻將凳及 BlockItem。
     *
     * @param path 方塊 registry 路徑。
     * @param design 麻將凳外觀與碰撞設計。
     * @param settings 建立原版方塊設定的函式。
     * @param seatService 乘客上下座服務。
     * @return 登記完成後可讀取方塊的項目。
     */
    private fun ModRegistrar.registerStool(
        path: String,
        design: MahjongStoolDesign,
        settings: () -> BlockBehaviour.Properties,
        seatService: MahjongStoolSeatService,
    ): RegisteredEntry<Block> = registerWithItem(path) {
        MahjongStoolBlock(
            settings = settings(),
            design = design,
            seatService = seatService,
        )
    }

    /**
     * 以同一個 ID 登記方塊與它的 BlockItem。
     *
     * @param path 方塊與物品的登記路徑。
     * @param factory 建立方塊的函式。
     * @return 登記完成後可讀取方塊的項目。
     */
    private fun ModRegistrar.registerWithItem(path: String, factory: () -> Block): RegisteredEntry<Block> {
        val id = ResourceLocation(MinecraftModMetadata.MOD_ID, path)
        val block = register(registry = Registries.BLOCK, id = id, factory = factory)
        register(registry = Registries.ITEM, id = id) { BlockItem(block.get(), Item.Properties()) }
        return block
    }
}
