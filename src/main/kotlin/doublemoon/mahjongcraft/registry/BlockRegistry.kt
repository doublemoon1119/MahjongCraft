package doublemoon.mahjongcraft.registry

import doublemoon.mahjongcraft.block.MahjongStool
import doublemoon.mahjongcraft.block.MahjongTable
import doublemoon.mahjongcraft.id
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Blocks
import net.minecraft.block.piston.PistonBehavior
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.BlockSoundGroup

object BlockRegistry {

    val mahjongStool: MahjongStool = MahjongStool(
        AbstractBlock.Settings.copy(Blocks.OAK_WOOD)
            .pistonBehavior(PistonBehavior.DESTROY)
            .nonOpaque()
            .strength(0.3f)
            .sounds(BlockSoundGroup.WOOL)
    )

    val mahjongTable: MahjongTable = MahjongTable(
        AbstractBlock.Settings.copy(Blocks.OAK_WOOD)
            .pistonBehavior(PistonBehavior.IGNORE)
            .nonOpaque()
            .strength(0.7f)
            .sounds(BlockSoundGroup.WOOL)
    )

    fun register() {
        Registry.register(Registries.BLOCK, id("mahjong_stool"), mahjongStool)
        Registry.register(Registries.BLOCK, id("mahjong_table"), mahjongTable)
    }
}