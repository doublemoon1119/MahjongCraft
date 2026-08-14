package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/**
 * 小三元與大三元役滿檢測器。
 *
 * 三元系列役種要求手牌包含中、發、白三種三元牌的刻子（面子）。
 *
 * ## 小三元 (Shousangen) - 2 番
 * - 包含中、發、白三種三元牌中的兩種刻子
 * - 剩餘一組三元牌為雀頭
 *
 * 牌型結構（14 張）：
 * - 三元牌刻子 × 2（2 × 3 = 6 張）
 * - 三元牌雀頭（2 張）
 * - 任意面子 × 2（6 張）
 *
 * ## 大三元 (Daisangen) - 役滿
 * - 包含中、發、白三種三元牌的全部刻子
 * - 剩餘任意兩張牌形成雀頭
 *
 * 牌型結構（14 張）：
 * - 三元牌刻子 × 3（3 × 3 = 9 張）
 * - 任意面子 × 1（3 張）
 * - 任意雀頭（2 張）
 *
 * @param handStructure 手牌結構（由 [com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandDecomposer] 分割後的結果）。
 * @return 小三元或大三元結果，若不符合則返回 null。
 */
fun calculateSangaen(
    handStructure: HandStructure,
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 所有三元牌
    val dragonTiles = listOf(
        Tile.Honor.Red,
        Tile.Honor.Green,
        Tile.Honor.White,
    )

    // 收集所有面子（手牌 + 副露）
    val allMentsus = standard.mentsus + standard.fuuro.map { it.mentsu }

    // 計算所有三元牌的刻子
    val dragonKotsuCount = allMentsus.count { mentsu ->
        (
            mentsu is Mentsu.Kotsu ||
                mentsu is Mentsu.Ankan ||
                mentsu is Mentsu.Minkan ||
                mentsu is Mentsu.Kakan
            ) &&
            mentsu.tiles.all { it.riichiCanonical in dragonTiles }
    }

    // 三個三元牌刻子，必為大三元（役滿）
    if (dragonKotsuCount == 3) {
        return YakuResult.yakuman(YakuType.Daisangen)
    }

    // 兩個三元牌刻子，且雀頭為三元牌是為小三元（2 番）
    if (dragonKotsuCount == 2 && standard.pair.tile.riichiCanonical in dragonTiles) {
        return YakuResult.han(YakuType.Shousangen, 2)
    }

    return null
}
