package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.util.isHonor

/**
 * 字一色 (Tsuuiisou / All Honors) 役滿檢測器。
 *
 * 字一色是立直麻將中的役滿，條件如下：
 * - 手牌的全部 14 張牌都必須是字牌（東、南、西、北、白、發、中）
 * - 可以包含副露
 *
 * 牌型範例：
 * - 手牌：東東東、南南南、西西西、北北北、白白、發 (13 張)
 * - 胡牌：發
 *
 * @param hand 手牌（包含副露）。
 * @param winningTile 胡牌張。
 * @return 字一色役滿結果，若不符合則返回 null。
 */
fun calculateTsuuiisou(
    hand: Hand,
    winningTile: Tile,
): YakuResult? {
    // 取得所有牌
    val allTiles = hand.allTiles.map { it.tile.riichiCanonical } + winningTile.riichiCanonical

    // 檢查所有牌是否都為字牌
    val allAreHonor = allTiles.all { it.isHonor }

    // 有非字牌直接回傳 null
    if (!allAreHonor) return null

    return YakuResult.yakuman(YakuType.Tsuuiisou)
}
