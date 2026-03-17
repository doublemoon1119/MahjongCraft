package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType

/**
 * 平和 (Pinfu) 役種檢測器。
 *
 * 平和是立直麻將中最基本的一翻役，條件如下：
 * 1. 必須為門前清（無副露）
 * 2. 所有的面子都是順子（無刻子、槓）
 * 3. 雀頭不是役牌（字牌或么九牌）
 * 4. 听牌型為兩面聽（坦張）
 *
 * @param handStructure 手牌結構。
 * @param winningTile 胡牌張。
 * @param isMenzen 是否為門前清。
 * @return 平和役種結果，若不符合則返回 null。
 */
fun calculatePinfu(
    handStructure: HandStructure?,
    winningTile: Tile,
    isMenzen: Boolean
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 平和必須為門前清
    if (!isMenzen) {
        return null
    }

    // 檢查所有面子是否都是順子
    for (mentsu in standard.mentsus) {
        if (mentsu !is Mentsu.Shuntsu) {
            return null
        }
    }

    // 檢查雀頭是否為役牌（字牌或么九牌）
    val pairTile = standard.pair.tile
    if (isYakuhai(pairTile)) {
        return null
    }

    // 檢查聽牌型是否為兩面聽
    if (!isRyanmenTenpai(standard, winningTile)) {
        return null
    }

    return YakuResult.han(YakuType.Pinfu, 1)
}

/**
 * 檢查牌是否為役牌（字牌或么九牌）。
 */
private fun isYakuhai(tile: Tile): Boolean {
    return when (tile) {
        is Tile.Honor -> true
        is Tile.Numeric -> tile.value == 1 || tile.value == 9
        is Tile.Flower -> false
    }
}

/**
 * 檢查聽牌型是否為兩面聽（坦張）。
 *
 * 兩面聽是指聽牌時可以胡兩張牌，且這兩張牌是相鄰的。
 * 例如：聽 2, 3 萬（胡 1 萬或 4 萬），或聽 7, 8 萬（胡 6 萬或 9 萬）
 *
 * 在已完成的手牌中，如果 winningTile 在某個順子中：
 * - index=0 (headTile): 原本是邊張聽 (例如 23m 等待 1m 或 4m) -> 是兩面
 * - index=1 (middleTile): 原本是嵌張或兩面 (例如 12m 等待 3m，或 23m 等待 1m 或 4m) -> 需進一步分析
 * - index=2 (tailTile): 原本是邊張聽 (例如 78m 等待 6m 或 9m) -> 是兩面
 *
 * 實際上，平和的兩面聽是指「搭子」的形狀為「兩面」
 * 即：12, 23, 34, 45, 56, 67, 78, 89 這些搭子
 *
 * @param standard 標準手牌結構。
 * @param winningTile 胡牌張。
 * @return 是否為兩面聽。
 */
private fun isRyanmenTenpai(standard: HandStructure.Standard, winningTile: Tile): Boolean {
    // 首先檢查是否為單騎聽（雀頭等待）
    if (standard.pair.tile == winningTile) {
        return false
    }

    // 找出 winningTile 在哪個順子中
    for (mentsu in standard.mentsus) {
        if (mentsu is Mentsu.Shuntsu) {
            val tiles = mentsu.tiles
            if (winningTile in tiles) {
                val index = tiles.indexOf(winningTile)
                // index=0 或 index=2 表示 winningTile 在順子的兩端
                // 這種情況下，原來的搭子是兩面搭子
                // 例如：waiting 23m，winning 1m (index=0) -> 變成 123m，搭子是 23 (兩面)
                // 例如：waiting 23m，winning 4m (index=2) -> 變成 234m，搭子是 23 (兩面)
                return index == 0 || index == 2
            }
        }
    }

    return false
}
