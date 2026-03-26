package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.util.isTerminal

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
        is Tile.Numeric -> tile.isTerminal
        is Tile.Flower -> false
    }
}

/**
 * 檢查此胡牌結構中，[winningTile] 是否是以「兩面聽（Ryanmen）」完成最後一組面子。
 *
 * 平和（Pinfu）成立的必要條件之一是聽牌形式必須為兩面。
 * 兩面聽是指：手中持有相鄰的兩張牌（如 23），聽其兩端的牌（1 或 4）。
 *
 * 在已完成的 [standard] 結構中：
 * 1. **排除單騎**：[winningTile] 不能是雀頭（Pair）。
 * 2. **排除嵌張**：[winningTile] 若為順子的中間張（index 1），必非兩面（如 24 聽 3）。
 * 3. **排除邊張**：
 *    - 若胡的是 3，且組成 123（index 2），代表原本是 12 邊張聽。
 *    - 若胡的是 7，且組成 789（index 0），代表原本是 89 邊張聽。
 * 4. **判定兩面**：
 *    - 若胡的是順子的第三張（index 2）且非 3（如 23 聽 4 組成 234）。
 *    - 若胡的是順子的第一張（index 0）且非 7（如 78 聽 6 組成 678）。
 *
 * @param standard 已經過拆解的標準手牌結構（4面子 + 1雀頭）。
 * @param winningTile 最終胡的那一張牌。
 * @return 若該結構下 [winningTile] 符合兩面聽定義則回傳 true。
 */
private fun isRyanmenTenpai(standard: HandStructure.Standard, winningTile: Tile): Boolean {
    // 1. 排除單騎（雀頭聽）
    if (standard.pair.tile == winningTile) return false

    // 2. 遍歷所有順子，尋找包含 winningTile 的組合
    for (mentsu in standard.mentsus) {
        if (mentsu is Mentsu.Shuntsu) {
            val tiles = mentsu.tiles // 升序排列，例如 [2, 3, 4]
            if (winningTile !in tiles) continue

            val index = tiles.indexOf(winningTile)
            val value = (winningTile as? Tile.Numeric)?.value ?: continue // 取得點數 (1-9)

            // 判斷是否為兩面：
            // 狀況 A: 胡的是順子的第三張 (index 2)，且這張牌不能是 3
            // (如果是 3，代表原本是 12，那是邊張)
            if (index == 2 && value != 3) return true

            // 狀況 B: 胡的是順子的第一張 (index 0)，且這張牌不能是 7
            // (如果是 7，代表原本是 89，那是邊張)
            if (index == 0 && value != 7) return true
        }
    }
    return false
}
