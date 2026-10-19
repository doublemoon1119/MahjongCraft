package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult

/** 計算簡易配對麻將的兩對完成與聽牌狀態。 */
class ExamplePairingShantenCalculator : ShantenCalculator {
    override fun calculate(hand: Hand): ShantenResult {
        if (hand.melds.isNotEmpty()) return ShantenResult.NotTenpai(1)
        val tiles = hand.standingTiles.map { ExamplePairingTileInterpretationPolicy.canonicalize(it.tile) }
        if (tiles.anyNotSupported()) return ShantenResult.NotTenpai(1)
        val counts = tiles.groupingBy { it }.eachCount()
        if (tiles.size == 4 && (counts.values.sorted() == listOf(2, 2) || counts.values.any { it == 4 })) {
            return ShantenResult.Complete
        }
        if (tiles.size == 3) {
            val triple = counts.entries.firstOrNull { it.value == 3 }?.key
            if (triple != null) return ShantenResult.Tenpai(listOf(triple))
            val pair = counts.entries.firstOrNull { it.value == 2 }?.key
            val single = counts.entries.firstOrNull { it.value == 1 }?.key
            if (pair != null && single != null) return ShantenResult.Tenpai(listOf(single))
        }
        return ShantenResult.NotTenpai(1)
    }

    /** 判斷牌面是否屬於簡易配對規則的萬子牌範圍。 */
    private fun List<Tile>.anyNotSupported(): Boolean = any { tile ->
        tile !is Tile.Numeric || tile.suit != Tile.Suit.Character || tile.value !in 1..8
    }
}
