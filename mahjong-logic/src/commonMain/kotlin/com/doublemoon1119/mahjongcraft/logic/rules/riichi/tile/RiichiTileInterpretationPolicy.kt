package com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy

/**
 * 將日麻赤五解讀為同花色的普通五，其他牌保持原樣。
 *
 * 赤寶牌番數與資源外觀仍應使用原始 [Tile.Extension] 判斷；此 policy 只負責一般牌面等價關係。
 */
object RiichiTileInterpretationPolicy : TileInterpretationPolicy {
    override fun canonicalize(tile: Tile): Tile = when ((tile as? Tile.Extension)?.typeId) {
        RiichiTileTypes.RED_FIVE_CHARACTER -> Tile.Numeric(Tile.Suit.Character, 5)
        RiichiTileTypes.RED_FIVE_DOT -> Tile.Numeric(Tile.Suit.Dot, 5)
        RiichiTileTypes.RED_FIVE_BAMBOO -> Tile.Numeric(Tile.Suit.Bamboo, 5)
        else -> tile
    }

    /**
     * 判斷 [tile] 是否為日麻內建赤五。
     *
     * @param tile 要檢查的牌。
     * @return 若牌是三種內建赤五之一則為 true。
     */
    fun isRedDora(tile: Tile): Boolean = (tile as? Tile.Extension)?.typeId in RiichiTileTypes.ALL
}
