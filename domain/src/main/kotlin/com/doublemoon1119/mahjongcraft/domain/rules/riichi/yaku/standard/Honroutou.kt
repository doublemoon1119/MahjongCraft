package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.util.isTerminal
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed

/**
 * 混老頭 (Honroutou) 役種檢測器。
 *
 * 混老頭是立直麻將中的二翻役，條件如下：
 * 手牌全部由老頭牌（1、9 數牌）或字牌組成。
 *
 * 老頭牌包括：萬子、筒子、條子的 1 和 9。
 * 字牌包括：東、南、西、北、白、發、中。
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張。
 * @return 混老頭役種結果，若不符合則返回 null。
 */
fun calculateHonroutou(
    hand: Hand,
    winningTile: Tile
): YakuResult? {
    val allTiles = (hand.allTiles.map { it.tile } + winningTile)
        .map { it.withoutRed }

    // 檢查每張牌是否為老頭牌或字牌
    for (tile in allTiles) {
        when (tile) {
            is Tile.Numeric -> {
                // 老頭牌為 1 或 9
                if (!tile.isTerminal) {
                    return null
                }
            }
            is Tile.Honor -> {
                // 字牌允許
            }
            is Tile.Flower -> {
                // 花牌不允許（麻將中不使用花牌）
                return null
            }
        }
    }

    // 全部都是么九牌，是清老頭 (役滿)
    if (allTiles.all { it.isTerminal }) {
        return null
    }

    return YakuResult.han(YakuType.Honroutou, 2)
}