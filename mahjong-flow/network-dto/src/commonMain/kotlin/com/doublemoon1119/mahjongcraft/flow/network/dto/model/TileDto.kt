package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import kotlinx.serialization.Serializable

/**
 * [Tile] 的網路 DTO。地區限定牌種（如花牌）一律以 [Extension] 承載穩定牌種 ID。
 */
@Serializable
sealed interface TileDto {
    @Serializable
    data class Numeric(val suit: SuitDto, val value: Int) : TileDto

    /** [Tile.Extension] 的網路 DTO。 */
    @Serializable
    data class Extension(val typeId: String) : TileDto

    @Serializable
    sealed interface Honor : TileDto {
        @Serializable data object East : Honor

        @Serializable data object South : Honor

        @Serializable data object West : Honor

        @Serializable data object North : Honor

        @Serializable data object Red : Honor

        @Serializable data object Green : Honor

        @Serializable data object White : Honor
    }
}

/** [Tile.Suit] 的網路 DTO。 */
@Serializable
enum class SuitDto { CHARACTER, DOT, BAMBOO }

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

fun Tile.Suit.toDto(): SuitDto = when (this) {
    Tile.Suit.Character -> SuitDto.CHARACTER
    Tile.Suit.Dot -> SuitDto.DOT
    Tile.Suit.Bamboo -> SuitDto.BAMBOO
}

fun SuitDto.toDomain(): Tile.Suit = when (this) {
    SuitDto.CHARACTER -> Tile.Suit.Character
    SuitDto.DOT -> Tile.Suit.Dot
    SuitDto.BAMBOO -> Tile.Suit.Bamboo
}
