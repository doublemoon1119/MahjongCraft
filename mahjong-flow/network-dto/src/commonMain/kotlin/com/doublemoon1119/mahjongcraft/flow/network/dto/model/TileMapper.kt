package com.doublemoon1119.mahjongcraft.flow.network.dto.model
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/**
 * 將領域資料轉換為網路 DTO。
 * @return 轉換後的資料。
 */
fun Tile.toDto(): TileDto = when (this) {
    is Tile.Numeric -> TileDto.Numeric(suit.toDto(), value)
    Tile.Honor.East -> TileDto.Honor.East
    Tile.Honor.South -> TileDto.Honor.South
    Tile.Honor.West -> TileDto.Honor.West
    Tile.Honor.North -> TileDto.Honor.North
    Tile.Honor.Red -> TileDto.Honor.Red
    Tile.Honor.Green -> TileDto.Honor.Green
    Tile.Honor.White -> TileDto.Honor.White
    is Tile.Extension -> TileDto.Extension(typeId.toString())
}

/**
 * 將網路 DTO 還原為領域資料。
 * @return 轉換後的資料。
 */
fun TileDto.toDomain(): Tile = when (this) {
    is TileDto.Numeric -> Tile.Numeric(suit.toDomain(), value)
    is TileDto.Extension -> Tile.Extension(TileTypeId.parse(typeId))
    TileDto.Honor.East -> Tile.Honor.East
    TileDto.Honor.South -> Tile.Honor.South
    TileDto.Honor.West -> Tile.Honor.West
    TileDto.Honor.North -> Tile.Honor.North
    TileDto.Honor.Red -> Tile.Honor.Red
    TileDto.Honor.Green -> Tile.Honor.Green
    TileDto.Honor.White -> Tile.Honor.White
}

/**
 * 將領域資料轉換為網路 DTO。
 * @return 轉換後的資料。
 */
fun Tile.Suit.toDto(): SuitDto = when (this) {
    Tile.Suit.Character -> SuitDto.CHARACTER
    Tile.Suit.Dot -> SuitDto.DOT
    Tile.Suit.Bamboo -> SuitDto.BAMBOO
}

/**
 * 將網路 DTO 還原為領域資料。
 * @return 轉換後的資料。
 */
fun SuitDto.toDomain(): Tile.Suit = when (this) {
    SuitDto.CHARACTER -> Tile.Suit.Character
    SuitDto.DOT -> Tile.Suit.Dot
    SuitDto.BAMBOO -> Tile.Suit.Bamboo
}
