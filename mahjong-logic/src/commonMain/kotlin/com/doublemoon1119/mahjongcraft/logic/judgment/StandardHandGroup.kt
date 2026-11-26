package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/** 立牌拆解後的一組順子或刻子，不包含已宣告的副露。 */
sealed interface StandardHandGroup {
    /**
     * 三張同花色的連續數牌。
     *
     * @property first 順子最小的牌，點數必須為1至7。
     */
    data class Sequence(val first: Tile.Numeric) : StandardHandGroup {
        init {
            require(first.value <= 7) { "A sequence must start at rank 1 through 7" }
        }

        override val tiles: List<Tile> get() = (0..2).map { Tile.Numeric(first.suit, first.value + it) }
    }

    /**
     * 三張相同的數牌或字牌。
     *
     * @property tile 刻子的牌面。
     */
    data class Triplet(val tile: Tile) : StandardHandGroup {
        init {
            require(tile is Tile.Numeric || tile is Tile.Honor) { "A standard triplet requires a numeric or honor tile" }
        }

        override val tiles: List<Tile> get() = List(3) { tile }
    }

    /** 這組面子包含的三張牌面。 */
    val tiles: List<Tile>
}
