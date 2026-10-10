package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/**
 * 將 [Tile] 轉換成 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun Tile.toPersistenceDto(): TilePersistenceDto = when (this) {
    is Tile.Numeric -> TilePersistenceDto.Numeric(suit.toPersistenceDto(), value)
    Tile.Honor.East -> TilePersistenceDto.Honor(HonorPersistenceValue.EAST)
    Tile.Honor.South -> TilePersistenceDto.Honor(HonorPersistenceValue.SOUTH)
    Tile.Honor.West -> TilePersistenceDto.Honor(HonorPersistenceValue.WEST)
    Tile.Honor.North -> TilePersistenceDto.Honor(HonorPersistenceValue.NORTH)
    Tile.Honor.Red -> TilePersistenceDto.Honor(HonorPersistenceValue.RED)
    Tile.Honor.Green -> TilePersistenceDto.Honor(HonorPersistenceValue.GREEN)
    Tile.Honor.White -> TilePersistenceDto.Honor(HonorPersistenceValue.WHITE)
    is Tile.Extension -> TilePersistenceDto.Extension(typeId.toString())
}

/**
 * 將 [TilePersistenceDto] 還原成 [Tile]。
 *
 * @return 轉換後的結果。
 */
fun TilePersistenceDto.toDomain(): Tile = when (this) {
    is TilePersistenceDto.Numeric -> Tile.Numeric(suit.toDomain(), value)
    is TilePersistenceDto.Extension -> Tile.Extension(TileTypeId.parse(typeId))
    is TilePersistenceDto.Honor -> value.toDomain()
}

private fun Tile.Suit.toPersistenceDto(): SuitPersistenceDto = when (this) {
    Tile.Suit.Character -> SuitPersistenceDto.CHARACTER
    Tile.Suit.Dot -> SuitPersistenceDto.DOT
    Tile.Suit.Bamboo -> SuitPersistenceDto.BAMBOO
}

private fun SuitPersistenceDto.toDomain(): Tile.Suit = when (this) {
    SuitPersistenceDto.CHARACTER -> Tile.Suit.Character
    SuitPersistenceDto.DOT -> Tile.Suit.Dot
    SuitPersistenceDto.BAMBOO -> Tile.Suit.Bamboo
}

private fun HonorPersistenceValue.toDomain(): Tile.Honor = when (this) {
    HonorPersistenceValue.EAST -> Tile.Honor.East
    HonorPersistenceValue.SOUTH -> Tile.Honor.South
    HonorPersistenceValue.WEST -> Tile.Honor.West
    HonorPersistenceValue.NORTH -> Tile.Honor.North
    HonorPersistenceValue.RED -> Tile.Honor.Red
    HonorPersistenceValue.GREEN -> Tile.Honor.Green
    HonorPersistenceValue.WHITE -> Tile.Honor.White
}
