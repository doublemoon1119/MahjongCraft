package doublemoon.mahjongcraft.registry

import doublemoon.mahjongcraft.entity.*
import doublemoon.mahjongcraft.id
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry

//參考: https://fabricmc.net/wiki/tutorial:entity
object EntityTypeRegistry {

    private val zeroDimension = EntityDimensions.fixed(0f, 0f)

    val seat: EntityType<SeatEntity> = FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::SeatEntity)
        .dimensions(zeroDimension)
        .build()

    val dice: EntityType<DiceEntity> = FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::DiceEntity)
        .dimensions(EntityDimensions.fixed(DiceEntity.DICE_WIDTH, DiceEntity.DICE_HEIGHT))
        .build()

    val mahjongTile: EntityType<MahjongTileEntity> =
        FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MahjongTileEntity)
            .dimensions(
                EntityDimensions.fixed(
                    MahjongTileEntity.MAHJONG_TILE_WIDTH,
                    MahjongTileEntity.MAHJONG_TILE_HEIGHT
                )
            ).build()

    val mahjongScoringStick: EntityType<MahjongScoringStickEntity> =
        FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MahjongScoringStickEntity)
            .dimensions(
                EntityDimensions.fixed(
                    MahjongScoringStickEntity.MAHJONG_POINT_STICK_WIDTH,
                    MahjongScoringStickEntity.MAHJONG_POINT_STICK_HEIGHT
                )
            ).build()

    val mahjongBot: EntityType<MahjongBotEntity> =
        FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MahjongBotEntity)
            .dimensions(
                EntityDimensions.fixed(
                    MahjongBotEntity.MAHJONG_BOT_WIDTH,
                    MahjongBotEntity.MAHJONG_BOT_HEIGHT
                )
            ).build()

    fun register() {
        Registry.register(Registries.ENTITY_TYPE, id("seat"), seat)
        Registry.register(Registries.ENTITY_TYPE, id("dice"), dice)
        Registry.register(Registries.ENTITY_TYPE, id("mahjong_bot"), mahjongBot)
        Registry.register(Registries.ENTITY_TYPE, id("mahjong_tile"), mahjongTile)
        Registry.register(Registries.ENTITY_TYPE, id("mahjong_scoring_stick"), mahjongScoringStick)
    }

}