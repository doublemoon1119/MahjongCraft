package com.doublemoon1119.mahjongcraft.domain.judgment

/**
 * 表示向聽數計算結果的資料結構。
 *
 * @property shanten 向聽數。-1 代表已胡牌，0 代表聽牌，正數代表距離聽牌還差幾張牌。
 */
data class ShantenResult(
    val shanten: Int
)
