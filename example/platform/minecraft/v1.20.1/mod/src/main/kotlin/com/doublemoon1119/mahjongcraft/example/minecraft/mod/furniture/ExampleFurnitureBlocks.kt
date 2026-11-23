package com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture

import com.doublemoon1119.mahjongcraft.example.minecraft.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.block.ExampleBenchBlock
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.block.ExampleChairBlock
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleRegistrar
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState

/** 範例家具方塊與對應物品的集中註冊點。 */
object ExampleFurnitureBlocks {
    /** 範例方塊椅子；寫入登記表後可用。 */
    lateinit var chair: Block
        private set

    /** 每格一席的可延長長凳；寫入登記表後可用。 */
    lateinit var bench: Block
        private set

    /** 判斷方塊是否使用範例的方塊座位服務。 */
    fun isSeat(state: BlockState): Boolean = isRegistered() && (state.`is`(chair) || state.`is`(bench))

    /** 確認方塊座位是否已完成原生註冊。 */
    fun isRegistered(): Boolean = ::chair.isInitialized && ::bench.isInitialized

    /**
     * 登記椅子、長凳與對應物品；座位保持實心，不被水流沖走。
     *
     * @param registrar 載入器的登記方式。
     */
    fun register(registrar: ExampleRegistrar) {
        registrar.register(
            registry = Registries.BLOCK,
            id = ResourceLocation(ExampleFurnitureIds.CHAIR),
            factory = { ExampleChairBlock(settings = seatSettings()) },
            onRegistered = { chair = it },
        )
        registrar.register(Registries.ITEM, ResourceLocation(ExampleFurnitureIds.CHAIR), { BlockItem(chair, Item.Properties()) })
        registrar.register(
            registry = Registries.BLOCK,
            id = ResourceLocation(ExampleFurnitureIds.BENCH),
            factory = { ExampleBenchBlock(seatSettings()) },
            onRegistered = { bench = it },
        )
        registrar.register(Registries.ITEM, ResourceLocation(ExampleFurnitureIds.BENCH), { BlockItem(bench, Item.Properties()) })
    }

    /**
     * 座位方塊共用的設定。
     *
     * @return 以橡木木材為基礎、保持實心的設定。
     */
    private fun seatSettings(): BlockBehaviour.Properties = BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(1.5f).forceSolidOn()
}
