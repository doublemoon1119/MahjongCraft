package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.util.isTerminal
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed

/**
 * 清老頭役滿檢測器。
 *
 * 清老頭是立直麻將中的役滿，條件如下：
 * 手牌全部由老頭牌（1、9 數牌）組成，不含任何字牌。
 *
 * 老頭牌包括：萬子、筒子、條子的 1 和 9。
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張。
 * @return 清老頭役滿結果，若不符合則返回 null。
 */
fun calculateChinroutou(
    hand: Hand,
    winningTile: Tile
): YakuResult? {
    val allTiles = (hand.allTiles.map { it.tile } + winningTile)
        .map { it.withoutRed }

    // 全部都是老頭牌（1、9 數牌），是清老頭
    if (allTiles.all { it.isTerminal }) {
        return YakuResult.yakuman(YakuType.Chinroutou)
    }

    return null
}
