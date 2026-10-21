package com.doublemoon1119.mahjongcraft.example.fabric.furniture

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleChairSeatEntity
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleCushionEntity
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

/** 範例家具 entity type 的集中註冊點。 */
object ExampleFurnitureEntities {
    /** 椅子使用的隱形暫時座位；由 [register] 初始化。 */
    lateinit var chairSeat: EntityType<ExampleChairSeatEntity>
        private set

    /** 確認範例 entity type 是否已完成原生註冊。 */
    fun isRegistered(): Boolean = ::chairSeat.isInitialized && ::cushion.isInitialized

    /** 可保存的坐墊 entity；由 [register] 初始化。 */
    lateinit var cushion: EntityType<ExampleCushionEntity>
        private set

    /** 註冊範例家具的兩種 entity type；兩者都不會自然生成。 */
    fun register() {
        chairSeat = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(ExampleFurnitureIds.CHAIR_SEAT),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::ExampleChairSeatEntity)
                .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                .trackRangeChunks(10)
                .trackedUpdateRate(10)
                .disableSaving()
                .fireImmune()
                .build(),
        )
        cushion = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(ExampleFurnitureIds.CUSHION),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::ExampleCushionEntity)
                .dimensions(EntityDimensions.fixed(0.75f, 0.2f))
                .trackRangeChunks(10)
                .trackedUpdateRate(10)
                .fireImmune()
                .build(),
        )
    }
}
