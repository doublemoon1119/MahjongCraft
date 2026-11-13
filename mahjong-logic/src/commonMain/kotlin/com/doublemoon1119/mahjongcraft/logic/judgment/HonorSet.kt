package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 一整套字牌：三種三元牌或四種風牌。
 *
 * @property tiles 這套字牌包含的牌。
 */
enum class HonorSet(val tiles: Set<Tile>) {
    /** 中、發、白三種三元牌。 */
    DRAGONS(setOf(Tile.Honor.Red, Tile.Honor.Green, Tile.Honor.White)),

    /** 東、南、西、北四種風牌。 */
    WINDS(setOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North)),
}
