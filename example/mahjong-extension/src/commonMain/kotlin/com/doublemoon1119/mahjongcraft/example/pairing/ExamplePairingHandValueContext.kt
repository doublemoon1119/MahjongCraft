package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueContext
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 簡易配對麻將的胡牌計算上下文。
 *
 * @property hand 胡牌當下的手牌。
 * @property winningTileValue 使手牌完成的完整牌值。
 * @property isTsumo 是否為自摸。
 * @property isMenzen 是否沒有副露。
 * @property roundWind 當前場風。
 * @property seatWind 胡牌玩家的自風。
 */
data class ExamplePairingHandValueContext(
    override val hand: Hand,
    override val winningTileValue: TileValue,
    override val isTsumo: Boolean,
    override val isMenzen: Boolean,
    override val roundWind: Wind,
    override val seatWind: Wind,
) : HandValueContext
