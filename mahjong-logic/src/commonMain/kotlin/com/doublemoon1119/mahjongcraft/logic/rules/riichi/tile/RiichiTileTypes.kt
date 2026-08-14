package com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy

/** 日麻規則提供的擴充牌種穩定 ID。 */
object RiichiTileTypes {
    /** 赤五萬。 */
    val RED_FIVE_CHARACTER: TileTypeId = TileTypeId.parse("mahjongcraft:riichi/red_five_character")

    /** 赤五筒。 */
    val RED_FIVE_DOT: TileTypeId = TileTypeId.parse("mahjongcraft:riichi/red_five_dot")

    /** 赤五條。 */
    val RED_FIVE_BAMBOO: TileTypeId = TileTypeId.parse("mahjongcraft:riichi/red_five_bamboo")

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

/**
 * 將日麻赤五解讀為同花色的普通五，其他牌保持原樣。
 *
 * 赤寶牌番數與資源外觀仍應使用原始 [Tile.Extension] 判斷；此 policy 只負責一般牌面等價關係。
 */
object RiichiTileInterpretationPolicy : TileInterpretationPolicy {
    override fun canonicalize(tile: Tile): Tile {
        if (tile is Tile.Numeric && tile.isRed) return tile.copy(isRed = false)
        return when ((tile as? Tile.Extension)?.typeId) {
            RiichiTileTypes.RED_FIVE_CHARACTER -> Tile.Numeric(Tile.Suit.Character, 5)
            RiichiTileTypes.RED_FIVE_DOT -> Tile.Numeric(Tile.Suit.Dot, 5)
            RiichiTileTypes.RED_FIVE_BAMBOO -> Tile.Numeric(Tile.Suit.Bamboo, 5)
            else -> tile
        }
    }

    /** 判斷 [tile] 是否為日麻內建赤五。 */
    fun isRedDora(tile: Tile): Boolean = (tile is Tile.Numeric && tile.isRed) ||
        (tile as? Tile.Extension)?.typeId in RiichiTileTypes.ALL
}
