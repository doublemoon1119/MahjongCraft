package com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 日麻規則提供的擴充牌種穩定 ID。 */
object RiichiTileTypes {
    /** 赤五萬。 */
    val RED_FIVE_CHARACTER: TileTypeId = TileTypeId.parse(MahjongCraftMetadata.id("riichi/red_five_character"))

    /** 赤五筒。 */
    val RED_FIVE_DOT: TileTypeId = TileTypeId.parse(MahjongCraftMetadata.id("riichi/red_five_dot"))

    /** 赤五條。 */
    val RED_FIVE_BAMBOO: TileTypeId = TileTypeId.parse(MahjongCraftMetadata.id("riichi/red_five_bamboo"))

    /** 依萬、筒、條順序排列的全部內建赤五 ID。 */
    val ALL: List<TileTypeId> = listOf(RED_FIVE_CHARACTER, RED_FIVE_DOT, RED_FIVE_BAMBOO)

    /** 建立指定花色的赤五擴充牌。 */
    fun redFive(suit: Tile.Suit): Tile.Extension = Tile.Extension(
        when (suit) {
            Tile.Suit.Character -> RED_FIVE_CHARACTER
            Tile.Suit.Dot -> RED_FIVE_DOT
            Tile.Suit.Bamboo -> RED_FIVE_BAMBOO
        },
    )
}

/** 將日麻擴充牌轉成一般牌型運算使用的標準牌。 */
val Tile.riichiCanonical: Tile
    get() = RiichiTileInterpretationPolicy.canonicalize(this)
