package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueContext
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 台灣麻將計台所需的牌況與已確認情境。
 *
 * @property hand 不含胡牌張的手牌及已宣告副露。
 * @property winningTileValue 獨立傳入的胡牌張。
 * @property isTsumo 是否自摸。
 * @property roundWind 本圈風。
 * @property seatWind 玩家門風。
 * @property flowers 已補出的花牌面，不帶牌張識別碼。
 * @property situation 胡牌時的有效情境與資格。
 */
data class TaiwanHandValueContext(
    override val hand: Hand,
    override val winningTileValue: TileValue,
    override val isTsumo: Boolean,
    override val roundWind: Wind,
    override val seatWind: Wind,
    val flowers: List<Tile>,
    val situation: TaiwanWinSituation,
) : HandValueContext {
    override val isMenzen: Boolean get() = hand.melds.all { it.type == MeldType.CLOSED_KAN }
}
