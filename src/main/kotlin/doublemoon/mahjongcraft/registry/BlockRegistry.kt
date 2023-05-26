package doublemoon.mahjongcraft.registry

import doublemoon.mahjongcraft.block.MahjongStool
import doublemoon.mahjongcraft.block.MahjongTable
import doublemoon.mahjongcraft.id
import doublemoon.mahjongcraft.itemGroup
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.block.Material
import net.minecraft.item.BlockItem
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.BlockSoundGroup

object BlockRegistry {

    private val mahjongStool: MahjongStool = MahjongStool(
        FabricBlockSettings.of(Material.WOOD)
            .nonOpaque()
            .strength(0.3f)
            .sounds(BlockSoundGroup.WOOL)
    )

    val mahjongTable: MahjongTable = MahjongTable(
        FabricBlockSettings.of(Material.WOOD)
            .nonOpaque()
            .strength(0.7f)
            .sounds(BlockSoundGroup.WOOL)
    )

    private fun registerBlocks() {
        Registry.register(Registries.BLOCK, id("mahjong_stool"), mahjongStool)
        Registry.register(Registries.BLOCK, id("mahjong_table"), mahjongTable)
    }

    private fun registerBlockItems() {
        val mahjongStoolBlockItem = BlockItem(mahjongStool, FabricItemSettings())
        val mahjongTableBlockItem = BlockItem(mahjongTable, FabricItemSettings())
        Registry.register(Registries.ITEM, id("mahjong_stool"), mahjongStoolBlockItem)
        Registry.register(Registries.ITEM, id("mahjong_table"), mahjongTableBlockItem)
        ItemGroupEvents.modifyEntriesEvent(itemGroup).register {
            it.add(mahjongStoolBlockItem)
            it.add(mahjongTableBlockItem)
        }
    }

    fun register() {
        registerBlocks()
        registerBlockItems()
    }
}