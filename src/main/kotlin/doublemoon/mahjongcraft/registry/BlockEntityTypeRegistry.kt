package doublemoon.mahjongcraft.registry

import doublemoon.mahjongcraft.blockentity.MahjongTableBlockEntity
import doublemoon.mahjongcraft.id
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.util.registry.Registry

object BlockEntityTypeRegistry {

    val mahjongTable: BlockEntityType<MahjongTableBlockEntity> =
        BlockEntityType.Builder.create(::MahjongTableBlockEntity, BlockRegistry.mahjongTable).build(null)

    fun register() {
        Registry.register(Registry.BLOCK_ENTITY_TYPE, id("mahjong_table"), mahjongTable)
    }

}