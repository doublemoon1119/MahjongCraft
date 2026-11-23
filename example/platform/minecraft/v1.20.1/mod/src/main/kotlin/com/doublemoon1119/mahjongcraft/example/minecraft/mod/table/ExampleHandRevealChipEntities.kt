package com.doublemoon1119.mahjongcraft.example.minecraft.mod.table

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleEntityTypes
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleRegistrar
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleMinecraftPresentationIds
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleTablePropDefinitions
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType

/** 範例攤牌籌碼的原生 entity type 註冊點。 */
object ExampleHandRevealChipEntities {
    /** 範例攤牌籌碼 entity type；寫入登記表後可用。 */
    lateinit var handRevealChip: EntityType<ExampleHandRevealChipEntity>
        private set

    /**
     * 登記範例攤牌籌碼 entity；玩家在一個區塊內才會收到。
     *
     * @param registrar 載入器的登記方式。
     */
    fun register(registrar: ExampleRegistrar) {
        val dimensions = ExampleTablePropDefinitions.HAND_REVEAL_CHIP.dimensions
        registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(ExampleMinecraftPresentationIds.HAND_REVEAL_CHIP_KIND),
            factory = {
                ExampleEntityTypes.create(
                    factory = ::ExampleHandRevealChipEntity,
                    width = dimensions.width.toFloat(),
                    height = dimensions.height.toFloat(),
                    trackingRangeChunks = 1,
                    updateInterval = 10,
                    fireImmune = true,
                )
            },
            onRegistered = { handRevealChip = it },
        )
    }
}
