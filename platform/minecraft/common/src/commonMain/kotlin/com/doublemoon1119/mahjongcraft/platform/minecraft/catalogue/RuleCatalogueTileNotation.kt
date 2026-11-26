package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView

/**
 * 以簡寫列出範例牌面，牌與牌之間以空白分隔。
 *
 * 數牌寫成花色字母加數字：`m` 萬子、`p` 筒子、`s` 索子，例如 `m1`、`p5`。字牌寫成 `E` 東、`S` 南、`W` 西、`N` 北、
 * `Wh` 白、`G` 發、`R` 中。
 *
 * @param notation 簡寫，例如 `"p1 p2 p3 R R R E"`。
 * @return 依順序排列的牌面。
 * @throws IllegalArgumentException 簡寫中有無法辨識的牌。
 */
internal fun catalogueTiles(notation: String): List<TileFaceView> = notation.split(' ').filter(String::isNotEmpty).map(::catalogueTile)

/**
 * 解析一張牌的簡寫。
 *
 * @param code 一張牌的簡寫。
 * @return 牌面。
 * @throws IllegalArgumentException 無法辨識的簡寫。
 */
private fun catalogueTile(code: String): TileFaceView {
    val honor = HONORS[code]
    if (honor != null) return TileFaceView(honor)
    val suit = SUITS[code.first()]
    val value = code.drop(1).toIntOrNull()
    require(code.length == 2 && suit != null && value != null && value in 1..9) { "Unknown catalogue tile notation: $code" }
    return TileFaceView(Tile.Numeric(suit, value))
}

/** 數牌花色的簡寫字母。 */
private val SUITS: Map<Char, Tile.Suit> = mapOf('m' to Tile.Suit.Character, 'p' to Tile.Suit.Dot, 's' to Tile.Suit.Bamboo)

/** 字牌的簡寫。 */
private val HONORS: Map<String, Tile> = mapOf(
    "E" to Tile.Honor.East,
    "S" to Tile.Honor.South,
    "W" to Tile.Honor.West,
    "N" to Tile.Honor.North,
    "Wh" to Tile.Honor.White,
    "G" to Tile.Honor.Green,
    "R" to Tile.Honor.Red,
)
