package com.doublemoon1119.mahjongcraft.example.fabric.table

import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleMinecraftPresentationIds
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleTablePropDefinitions
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

/** 範例攤牌籌碼的原生 entity type 註冊點。 */
object ExampleHandRevealChipEntities {
    /** 範例攤牌籌碼 entity type。 */
    lateinit var handRevealChip: EntityType<ExampleHandRevealChipEntity>
        private set

    /** 註冊範例攤牌籌碼 entity。 */
    fun register() {
        val dimensions = ExampleTablePropDefinitions.HAND_REVEAL_CHIP.dimensions
        handRevealChip = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation(ExampleMinecraftPresentationIds.HAND_REVEAL_CHIP_KIND),
            FabricEntityTypeBuilder.create(MobCategory.MISC, ::ExampleHandRevealChipEntity)
                .dimensions(EntityDimensions.fixed(dimensions.width.toFloat(), dimensions.height.toFloat()))
                .trackRangeBlocks(16)
                .trackedUpdateRate(10)
                .fireImmune()
                .build(),
        )
    }
}
