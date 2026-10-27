package com.doublemoon1119.mahjongcraft.example.fabric.table

import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleMinecraftPresentationIds
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleTablePropDefinitions
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

/** 範例宣告籌碼的原生 entity type 註冊點。 */
object ExampleDeclarationChipEntities {
    /** 範例宣告籌碼 entity type。 */
    lateinit var declarationChip: EntityType<ExampleDeclarationChipEntity>
        private set

    /** 註冊範例宣告籌碼 entity。 */
    fun register() {
        val dimensions = ExampleTablePropDefinitions.DECLARATION_CHIP.dimensions
        declarationChip = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(ExampleMinecraftPresentationIds.DECLARATION_CHIP_KIND),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::ExampleDeclarationChipEntity)
                .dimensions(EntityDimensions.fixed(dimensions.width.toFloat(), dimensions.height.toFloat()))
                .trackRangeBlocks(16)
                .trackedUpdateRate(10)
                .fireImmune()
                .build(),
        )
    }
}
