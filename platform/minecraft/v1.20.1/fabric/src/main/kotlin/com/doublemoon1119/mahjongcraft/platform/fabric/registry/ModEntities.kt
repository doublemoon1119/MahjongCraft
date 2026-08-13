package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

/** MahjongCraft Fabric entity type 的集中註冊點。 */
object ModEntities {
    /** 麻將牌 entity type；由 [register] 初始化。 */
    lateinit var mahjongTile: EntityType<MahjongTileEntity>
        private set

    /** 註冊不自然生成的輕量麻將牌 entity。 */
    fun register() {
        mahjongTile = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "mahjong_tile"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MahjongTileEntity)
                .dimensions(EntityDimensions.fixed(MahjongTileEntity.TILE_WIDTH, MahjongTileEntity.TILE_HEIGHT))
                .trackRangeBlocks(16)
                .trackedUpdateRate(10)
                .build(),
        )
    }
}
