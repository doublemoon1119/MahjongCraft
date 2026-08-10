package com.doublemoon1119.mahjongcraft.flow.network.dto

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlinx.serialization.Serializable

/**
 * [Tile] 的線路 DTO。日麻不使用花牌，但 [Flower] 分支仍原樣鏡射，供未來台灣麻將規則使用。
 */
@Serializable
sealed interface TileDto {
    @Serializable
    data class Numeric(val suit: SuitDto, val value: Int, val isRed: Boolean = false) : TileDto

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

    @Serializable
    sealed interface Flower : TileDto {
        @Serializable data object Spring : Flower

        @Serializable data object Summer : Flower

        @Serializable data object Autumn : Flower

        @Serializable data object Winter : Flower

        @Serializable data object Plum : Flower

        @Serializable data object Orchid : Flower

        @Serializable data object Bamboo : Flower

        @Serializable data object Chrysanthemum : Flower
    }
}

@Serializable
enum class SuitDto { CHARACTER, DOT, BAMBOO }

fun Tile.toDto(): TileDto = when (this) {
    is Tile.Numeric -> TileDto.Numeric(suit.toDto(), value, isRed)
    Tile.Honor.East -> TileDto.Honor.East
    Tile.Honor.South -> TileDto.Honor.South
    Tile.Honor.West -> TileDto.Honor.West
    Tile.Honor.North -> TileDto.Honor.North
    Tile.Honor.Red -> TileDto.Honor.Red
    Tile.Honor.Green -> TileDto.Honor.Green
    Tile.Honor.White -> TileDto.Honor.White
    Tile.Flower.Spring -> TileDto.Flower.Spring
    Tile.Flower.Summer -> TileDto.Flower.Summer
    Tile.Flower.Autumn -> TileDto.Flower.Autumn
    Tile.Flower.Winter -> TileDto.Flower.Winter
    Tile.Flower.Plum -> TileDto.Flower.Plum
    Tile.Flower.Orchid -> TileDto.Flower.Orchid
    Tile.Flower.Bamboo -> TileDto.Flower.Bamboo
    Tile.Flower.Chrysanthemum -> TileDto.Flower.Chrysanthemum
}

fun TileDto.toDomain(): Tile = when (this) {
    is TileDto.Numeric -> Tile.Numeric(suit.toDomain(), value, isRed)
    TileDto.Honor.East -> Tile.Honor.East
    TileDto.Honor.South -> Tile.Honor.South
    TileDto.Honor.West -> Tile.Honor.West
    TileDto.Honor.North -> Tile.Honor.North
    TileDto.Honor.Red -> Tile.Honor.Red
    TileDto.Honor.Green -> Tile.Honor.Green
    TileDto.Honor.White -> Tile.Honor.White
    TileDto.Flower.Spring -> Tile.Flower.Spring
    TileDto.Flower.Summer -> Tile.Flower.Summer
    TileDto.Flower.Autumn -> Tile.Flower.Autumn
    TileDto.Flower.Winter -> Tile.Flower.Winter
    TileDto.Flower.Plum -> Tile.Flower.Plum
    TileDto.Flower.Orchid -> Tile.Flower.Orchid
    TileDto.Flower.Bamboo -> Tile.Flower.Bamboo
    TileDto.Flower.Chrysanthemum -> Tile.Flower.Chrysanthemum
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
