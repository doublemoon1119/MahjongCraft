package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 一種標準和牌拆法，並標明和牌張在其中的用途。
 *
 * @property pair 雀頭的牌面。
 * @property groups 立牌拆出的面子，不含已宣告的副露。
 * @property winningGroupIndex 和牌張完成的面子索引；為null時完成雀頭。
 */
data class StandardHandDecomposition(
    val pair: Tile,
    val groups: List<StandardHandGroup>,
    val winningGroupIndex: Int?,
) {
    init {
        require(pair is Tile.Numeric || pair is Tile.Honor) { "A pair requires a numeric or honor tile" }
        require(winningGroupIndex == null || winningGroupIndex in groups.indices) { "Winning group must belong to the decomposition" }
    }
}
