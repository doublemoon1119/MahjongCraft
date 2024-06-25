package doublemoon.mahjongcraft.registry

import doublemoon.mahjongcraft.entity.*
import doublemoon.mahjongcraft.id
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry

//參考: https://fabricmc.net/wiki/tutorial:entity
object EntityTypeRegistry {

    val seat: EntityType<SeatEntity> = EntityType.Builder.create(::SeatEntity, SpawnGroup.MISC)
        .dimensions(0f, 0f)
        .build()

    val dice: EntityType<DiceEntity> = EntityType.Builder.create(::DiceEntity, SpawnGroup.MISC)
        .dimensions(DiceEntity.DICE_WIDTH, DiceEntity.DICE_HEIGHT)
        .build()

    val mahjongTile: EntityType<MahjongTileEntity> = EntityType.Builder.create(::MahjongTileEntity, SpawnGroup.MISC)
        .dimensions(MahjongTileEntity.MAHJONG_TILE_WIDTH, MahjongTileEntity.MAHJONG_TILE_HEIGHT)
        .build()

    val mahjongScoringStick: EntityType<MahjongScoringStickEntity> =
        EntityType.Builder.create(::MahjongScoringStickEntity, SpawnGroup.MISC)
            .dimensions(
                MahjongScoringStickEntity.MAHJONG_POINT_STICK_WIDTH,
                MahjongScoringStickEntity.MAHJONG_POINT_STICK_HEIGHT
            ).build()

    val mahjongBot: EntityType<MahjongBotEntity> = EntityType.Builder.create(::MahjongBotEntity, SpawnGroup.MISC)
        .dimensions(MahjongBotEntity.MAHJONG_BOT_WIDTH, MahjongBotEntity.MAHJONG_BOT_HEIGHT)
        .build()

    fun register() {
        Registry.register(Registries.ENTITY_TYPE, id("seat"), seat)
        Registry.register(Registries.ENTITY_TYPE, id("dice"), dice)
        Registry.register(Registries.ENTITY_TYPE, id("mahjong_bot"), mahjongBot)
        Registry.register(Registries.ENTITY_TYPE, id("mahjong_tile"), mahjongTile)
        Registry.register(Registries.ENTITY_TYPE, id("mahjong_scoring_stick"), mahjongScoringStick)
    }

}