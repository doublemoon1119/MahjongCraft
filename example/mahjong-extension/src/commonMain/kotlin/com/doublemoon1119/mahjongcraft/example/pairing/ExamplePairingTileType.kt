package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/** 簡易配對麻將倒置一萬的牌面工廠。 */
object ExamplePairingTileType {
    /**
     * 建立保留自身牌種 ID、但配對時等同一萬的倒置牌。
     *
     * @return 範例專用牌種的牌面。
     */
    fun invertedOneMan(): Tile = Tile.Extension(ExamplePairingIds.INVERTED_ONE_MAN)
}
