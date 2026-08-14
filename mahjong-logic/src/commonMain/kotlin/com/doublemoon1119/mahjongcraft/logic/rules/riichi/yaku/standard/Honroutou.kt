package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.util.isTerminal

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
    winningTile: Tile,
): YakuResult? {
    val allTiles = (hand.allTiles.map { it.tile } + winningTile)
        .map { it.riichiCanonical }

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
            is Tile.Extension -> {
                // 未由日麻規則定義的擴充牌不屬於老頭牌或字牌
                return null
            }
        }
    }

    // 全部都是老頭牌，是清老頭 (役滿)
    if (allTiles.all { it.isTerminal }) {
        return null
    }

    return YakuResult.han(YakuType.Honroutou, 2)
}
