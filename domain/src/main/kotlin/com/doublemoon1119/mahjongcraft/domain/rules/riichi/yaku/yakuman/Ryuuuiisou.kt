package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed

/**
 * 綠一色 (Ryuuuiisou / All Green) 役滿檢測器。
 *
 * 綠一色是立直麻將中的役滿，條件如下：
 * - 手牌的全部 14 張牌都必須是綠牌
 * - 綠牌包含：2 索、3 索、4 索、6 索、8 索、發
 * - 可以包含副露
 *
 * 牌型範例：
 * - 手牌：2s 2s 2s、3s 3s 3s、4s 4s 4s、6s 6s、8s 8s
 * - 胡牌：發
 *
 * @param hand 手牌（包含副露）。
 * @param winningTile 胡牌張。
 * @return 綠一色役滿結果，若不符合則返回 null。
 */
fun calculateRyuuuiisou(
    hand: Hand,
    winningTile: Tile
): YakuResult? {
    /**
     * 綠牌的集合。
     *
     * 包含 2 索、3 索、4 索、6 索、8 索以及發。
     * 這些牌在傳統麻將中顯示為綠色。
     */
    val greenTiles = setOf(
        Tile.Numeric(Tile.Suit.Bamboo, 2),
        Tile.Numeric(Tile.Suit.Bamboo, 3),
        Tile.Numeric(Tile.Suit.Bamboo, 4),
        Tile.Numeric(Tile.Suit.Bamboo, 6),
        Tile.Numeric(Tile.Suit.Bamboo, 8),
        Tile.Honor.Green
    )

    // 取得所有牌（去除赤寶牌標記）
    val allTiles = hand.allTiles.map { it.tile.withoutRed } + winningTile.withoutRed

    // 檢查所有牌是否都為綠牌
    val allAreGreen = allTiles.all { it in greenTiles }

    // 有非綠牌直接回傳 null
    if (!allAreGreen) return null

    return YakuResult.yakuman(YakuType.Ryuuuiisou)
}
