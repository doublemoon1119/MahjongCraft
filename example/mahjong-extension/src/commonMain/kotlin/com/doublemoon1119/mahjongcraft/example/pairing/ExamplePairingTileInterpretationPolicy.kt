package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy

/** 將倒置一萬解讀成一般一萬，讓配對判定維持相同牌面語意。 */
object ExamplePairingTileInterpretationPolicy : TileInterpretationPolicy {
    override fun canonicalize(tile: Tile): Tile = if (tile is Tile.Extension && tile.typeId == ExamplePairingIds.INVERTED_ONE_MAN) {
        Tile.Numeric(Tile.Suit.Character, 1)
    } else {
        tile
    }
}
