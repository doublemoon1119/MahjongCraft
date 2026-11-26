package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder

/** 簡易配對麻將依牌面與倒置一萬排序的規則。 */
object ExamplePairingTileOrder : TileOrder {
    override fun compare(first: Tile, second: Tile): Int = weight(first).compareTo(weight(second))

    /** 將牌面轉成穩定排序權重。 */
    private fun weight(tile: Tile): Int = when (tile) {
        is Tile.Numeric -> tile.value
        is Tile.Extension -> if (tile.typeId == ExamplePairingIds.INVERTED_ONE_MAN) 1 else 100
        is Tile.Honor -> when (tile) {
            Tile.Honor.East -> 200
            Tile.Honor.South -> 201
            Tile.Honor.West -> 202
            Tile.Honor.North -> 203
            Tile.Honor.Red -> 204
            Tile.Honor.Green -> 205
            Tile.Honor.White -> 206
        }
    }
}
