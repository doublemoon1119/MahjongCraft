package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/** 簡易配對麻將的擴充牌種與加分牌面工廠。 */
object ExamplePairingTileType {
    /**
     * 建立保留自身牌種 ID、但配對時等同一萬的倒置牌。
     *
     * @return 範例專用牌種的牌面。
     */
    fun invertedOneMan(): Tile = Tile.Extension(ExamplePairingIds.INVERTED_ONE_MAN)

    /**
     * 將加分牌數值轉成桌面與結算共用的展示牌面。
     *
     * @param value 本局加分牌的數值，範圍為 1～8。
     * @return 一使用倒置一萬，其餘使用對應萬子牌面。
     */
    fun bonusFace(value: Int): Tile {
        require(value in 1..8) { "Bonus tile value must be between 1 and 8" }
        return if (value == 1) invertedOneMan() else Tile.Numeric(Tile.Suit.Character, value)
    }
}
