package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/**
 * 小四喜與大四喜役滿檢測器。
 *
 * 四喜系列役種要求手牌包含東、南、西、北四種風牌的刻子（面子）。
 *
 * ## 小四喜 (Shousuushi) - 役滿
 * - 包含東、南、西、北四種風牌的刻子
 * - 雀頭由兩張風牌形成
 * - 門清或副露皆可
 *
 * 牌型結構（14 張）：
 * - 東南西北至少 3 個刻子（3 × 3 = 9 張）
 * - 一個任意面子 (順子、刻子不限)
 * - 兩張風牌作為雀頭（2 張）
 *
 * ## 大四喜 (Daisuushii) - 雙倍役滿
 * - 包含東、南、西、北四種風牌的刻子
 * - 剩餘兩張任意牌作為雀頭
 *
 * 牌型結構（14 張）：
 * - 東南西北各一個刻子（4 × 3 = 12 張）
 * - 單張任何非風牌（1 張）
 *
 * @param handStructure 手牌結構（由 [com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandDecomposer] 分割後的結果）。
 * @return 小四喜，大四喜結果，若不符合則返回 null。
 */
fun calculateSuushii(
    handStructure: HandStructure,
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 所有風牌
    val windTiles = listOf(
        Tile.Honor.East,
        Tile.Honor.South,
        Tile.Honor.West,
        Tile.Honor.North,
    )

    // 收集所有面子（手牌 + 副露）
    val allMentsus = standard.mentsus + standard.fuuro.map { it.mentsu }

    // 計算所有風牌的刻子
    val windKotsuCount = allMentsus.count { mentsu ->
        (
            mentsu is Mentsu.Kotsu ||
                mentsu is Mentsu.Ankan ||
                mentsu is Mentsu.Minkan ||
                mentsu is Mentsu.Kakan
            ) &&
            mentsu.tiles.all { it.riichiCanonical in windTiles }
    }

    // 風牌刻子數量至少要 3 才有可能湊齊四喜
    if (windKotsuCount < 3) {
        return null
    }

    // 四個風牌刻子，必為大四喜
    if (windKotsuCount == 4) {
        return YakuResult.doubleYakuman(YakuType.Daisuushii)
    }

    // 三個風牌刻子，且雀頭為風牌是為小四喜
    if (windKotsuCount == 3 && standard.pair.tile.riichiCanonical in windTiles) {
        return YakuResult.yakuman(YakuType.Shousuushi)
    }

    return null
}
