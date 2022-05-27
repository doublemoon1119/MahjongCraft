package doublemoon.mahjongcraft.registry

import doublemoon.mahjongcraft.block.MahjongStool
import doublemoon.mahjongcraft.block.MahjongTable
import doublemoon.mahjongcraft.id
import doublemoon.mahjongcraft.itemGroup
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.block.Material
import net.minecraft.item.BlockItem
import net.minecraft.sound.BlockSoundGroup
import net.minecraft.util.registry.Registry

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
        Registry.register(Registry.BLOCK, id("mahjong_stool"), mahjongStool)
        Registry.register(Registry.BLOCK, id("mahjong_table"), mahjongTable)
    }

    private fun registerBlockItems() {
        Registry.register(
            Registry.ITEM,
            id("mahjong_stool"),
            BlockItem(mahjongStool, FabricItemSettings().group(itemGroup))
        )
        Registry.register(
            Registry.ITEM,
            id("mahjong_table"),
            BlockItem(mahjongTable, FabricItemSettings().group(itemGroup))
        )
    }

    fun register() {
        registerBlocks()
        registerBlockItems()
    }
}