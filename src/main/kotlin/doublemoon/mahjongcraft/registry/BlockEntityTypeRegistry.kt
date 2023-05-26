package doublemoon.mahjongcraft.registry

import doublemoon.mahjongcraft.blockentity.MahjongTableBlockEntity
import doublemoon.mahjongcraft.id
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry

object BlockEntityTypeRegistry {

    val mahjongTable: BlockEntityType<MahjongTableBlockEntity> =
        BlockEntityType.Builder.create(::MahjongTableBlockEntity, BlockRegistry.mahjongTable).build(null)

    fun register() {
        Registry.register(Registries.BLOCK_ENTITY_TYPE, id("mahjong_table"), mahjongTable)
    }

}