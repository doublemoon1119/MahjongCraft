package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlinx.serialization.Serializable

/** [Tile] 的完整 persistence DTO。 */
@Serializable
sealed interface TilePersistenceDto {
    /** [Tile.Numeric] 的 persistence DTO。 */
    @Serializable
    data class Numeric(
        val suit: SuitPersistenceDto,
        val value: Int,
        val isRed: Boolean,
    ) : TilePersistenceDto

    /** [Tile.Honor] 的 persistence DTO。 */
    @Serializable
    data class Honor(val value: HonorPersistenceValue) : TilePersistenceDto

    /** [Tile.Flower] 的 persistence DTO。 */
    @Serializable
    data class Flower(val value: FlowerPersistenceValue) : TilePersistenceDto
}

/** [Tile.Suit] 的 persistence DTO。 */
@Serializable
enum class SuitPersistenceDto { CHARACTER, DOT, BAMBOO }

/** [Tile.Honor] 種類的 persistence 值。 */
@Serializable
enum class HonorPersistenceValue { EAST, SOUTH, WEST, NORTH, RED, GREEN, WHITE }

/** [Tile.Flower] 種類的 persistence 值。 */
@Serializable
enum class FlowerPersistenceValue { SPRING, SUMMER, AUTUMN, WINTER, PLUM, ORCHID, BAMBOO, CHRYSANTHEMUM }

/** 將 [Tile] 轉換成 persistence DTO。 */
fun Tile.toPersistenceDto(): TilePersistenceDto = when (this) {
    is Tile.Numeric -> TilePersistenceDto.Numeric(suit.toPersistenceDto(), value, isRed)
    Tile.Honor.East -> TilePersistenceDto.Honor(HonorPersistenceValue.EAST)
    Tile.Honor.South -> TilePersistenceDto.Honor(HonorPersistenceValue.SOUTH)
    Tile.Honor.West -> TilePersistenceDto.Honor(HonorPersistenceValue.WEST)
    Tile.Honor.North -> TilePersistenceDto.Honor(HonorPersistenceValue.NORTH)
    Tile.Honor.Red -> TilePersistenceDto.Honor(HonorPersistenceValue.RED)
    Tile.Honor.Green -> TilePersistenceDto.Honor(HonorPersistenceValue.GREEN)
    Tile.Honor.White -> TilePersistenceDto.Honor(HonorPersistenceValue.WHITE)
    Tile.Flower.Spring -> TilePersistenceDto.Flower(FlowerPersistenceValue.SPRING)
    Tile.Flower.Summer -> TilePersistenceDto.Flower(FlowerPersistenceValue.SUMMER)
    Tile.Flower.Autumn -> TilePersistenceDto.Flower(FlowerPersistenceValue.AUTUMN)
    Tile.Flower.Winter -> TilePersistenceDto.Flower(FlowerPersistenceValue.WINTER)
    Tile.Flower.Plum -> TilePersistenceDto.Flower(FlowerPersistenceValue.PLUM)
    Tile.Flower.Orchid -> TilePersistenceDto.Flower(FlowerPersistenceValue.ORCHID)
    Tile.Flower.Bamboo -> TilePersistenceDto.Flower(FlowerPersistenceValue.BAMBOO)
    Tile.Flower.Chrysanthemum -> TilePersistenceDto.Flower(FlowerPersistenceValue.CHRYSANTHEMUM)
    is Tile.Extension -> throw UnsupportedOperationException(
        "Extension tile persistence DTO is not implemented yet: $typeId",
    )
}

/** 將 [TilePersistenceDto] 還原成 [Tile]。 */
fun TilePersistenceDto.toDomain(): Tile = when (this) {
    is TilePersistenceDto.Numeric -> Tile.Numeric(suit.toDomain(), value, isRed)
    is TilePersistenceDto.Honor -> value.toDomain()
    is TilePersistenceDto.Flower -> value.toDomain()
}

/** 將 [Tile.Suit] 轉換成 persistence DTO。 */
private fun Tile.Suit.toPersistenceDto(): SuitPersistenceDto = when (this) {
    Tile.Suit.Character -> SuitPersistenceDto.CHARACTER
    Tile.Suit.Dot -> SuitPersistenceDto.DOT
    Tile.Suit.Bamboo -> SuitPersistenceDto.BAMBOO
}

/** 將 [SuitPersistenceDto] 還原成 [Tile.Suit]。 */
private fun SuitPersistenceDto.toDomain(): Tile.Suit = when (this) {
    SuitPersistenceDto.CHARACTER -> Tile.Suit.Character
    SuitPersistenceDto.DOT -> Tile.Suit.Dot
    SuitPersistenceDto.BAMBOO -> Tile.Suit.Bamboo
}

/** 將 [HonorPersistenceValue] 還原成 [Tile.Honor]。 */
private fun HonorPersistenceValue.toDomain(): Tile.Honor = when (this) {
    HonorPersistenceValue.EAST -> Tile.Honor.East
    HonorPersistenceValue.SOUTH -> Tile.Honor.South
    HonorPersistenceValue.WEST -> Tile.Honor.West
    HonorPersistenceValue.NORTH -> Tile.Honor.North
    HonorPersistenceValue.RED -> Tile.Honor.Red
    HonorPersistenceValue.GREEN -> Tile.Honor.Green
    HonorPersistenceValue.WHITE -> Tile.Honor.White
}

/** 將 [FlowerPersistenceValue] 還原成 [Tile.Flower]。 */
private fun FlowerPersistenceValue.toDomain(): Tile.Flower = when (this) {
    FlowerPersistenceValue.SPRING -> Tile.Flower.Spring
    FlowerPersistenceValue.SUMMER -> Tile.Flower.Summer
    FlowerPersistenceValue.AUTUMN -> Tile.Flower.Autumn
    FlowerPersistenceValue.WINTER -> Tile.Flower.Winter
    FlowerPersistenceValue.PLUM -> Tile.Flower.Plum
    FlowerPersistenceValue.ORCHID -> Tile.Flower.Orchid
    FlowerPersistenceValue.BAMBOO -> Tile.Flower.Bamboo
    FlowerPersistenceValue.CHRYSANTHEMUM -> Tile.Flower.Chrysanthemum
}
