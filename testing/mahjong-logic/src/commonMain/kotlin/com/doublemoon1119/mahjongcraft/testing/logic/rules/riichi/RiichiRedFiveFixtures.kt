package com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues

/**
 * 將立牌中指定花色的前 [count] 張普通五換成赤五，保留 UUID 與順序。
 *
 * @param suit 赤五的花色。
 * @param count 要換成赤五的張數。
 * @return 帶有赤五的手牌。
 */
fun Hand.withRedFives(suit: Tile.Suit, count: Int = 1): Hand {
    val red = RiichiTileValues.redFive(suit)
    var remaining = count
    val replaced = tiles.map { tile ->
        if (remaining > 0 && tile.tile == red.tile && tile.components.isEmpty) {
            remaining--
            tile.copy(components = red.components)
        } else {
            tile
        }
    }
    check(remaining == 0) { "Hand has fewer than $count plain fives of $suit" }
    return copy(tiles = replaced)
}
