package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile

/**
 * 日本麻將專用的牌河紀錄實體。
 *
 * 擴充基礎紀錄以支援日本麻將特有的立直狀態。
 *
 * @param tile 被打出的具有唯一標識的麻將牌。
 * @param isRiichi 標記該張牌是否為宣告立直時所打出的牌。
 * @param isTaken 標記該張牌是否已被其他玩家鳴走。
 */
class RiichiDiscardEntry(
    tile: IdentifiedTile,
    val isRiichi: Boolean = false,
    isTaken: Boolean = false,
) : DiscardPile.DiscardEntry(tile, isTaken) {
    override fun withTaken(): RiichiDiscardEntry = RiichiDiscardEntry(tile, isRiichi, isTaken = true)
}
