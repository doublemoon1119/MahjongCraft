package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed

/**
 * 九蓮寶燈 (Churen Poto) 役滿檢測器。
 *
 * 九蓮寶燈是立直麻將中的役滿，條件如下：
 * - 手牌必須為同一花色（萬子、筒子或條子）
 * - 包含 111 和 999
 * - 包含 2-8 各一張
 * - 合計 14 張牌（胡牌後）
 * - 必須為門前清（無副露）
 *
 * 九蓮寶燈九面 (Churen Poto 9-men) 為雙倍役滿：
 * - 指的是聽 9 張牌（聽 1-9 全部）
 * - 胡牌時，手牌有 111、999 以及 2-8 各 1 張（總共 13 張）
 *
 * 牌型範例（九蓮寶燈九面）：
 * - 牌型：111m 999m 2345678m
 * - 也就是說，1 有 3 張，9 有 3 張，2-8 各 1 張
 * - 聽牌：1-9 任意一張
 *
 * 牌型範例（一般九蓮寶燈）：
 * - 牌型：111m 999m 2345677m
 * - 聽牌：8m
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張。
 * @param handStructure 手牌結構（由 [com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandDecomposer] 分割後的結果）。
 * @param isMenzen 是否為門前清。
 * @return 九蓮寶燈役滿結果，若不符合則返回 null。
 */
fun calculateChurenPoto(
    hand: Hand,
    winningTile: Tile,
    handStructure: HandStructure?,
    isMenzen: Boolean
): YakuResult? {
    // 必須為門前清
    if (!isMenzen) {
        return null
    }

    // 必須是標準手牌結構
    if (handStructure !is HandStructure.Standard) return null

    // 取得所有牌
    val allTiles = hand.allTiles.map { it.tile.withoutRed } + winningTile.withoutRed

    if (allTiles.size != 14) {
        return null
    }

    // 檢查是否為同一花色
    val suits = allTiles.mapNotNull { (it as? Tile.Numeric)?.suit }.toSet()
    if (suits.size != 1) {
        return null
    }

    // 計算牌數
    val tileCounts = allTiles.groupingBy { it }.eachCount()

    // 檢查是否有 111 和 999
    val suit = suits.first()
    val oneCount = tileCounts[Tile.Numeric(suit, 1)] ?: 0
    val nineCount = tileCounts[Tile.Numeric(suit, 9)] ?: 0

    if (oneCount < 3 || nineCount < 3) {
        return null
    }

    // 檢查是否有 2-8（至少各 1 張）
    for (value in 2..8) {
        val count = tileCounts[Tile.Numeric(suit, value)] ?: 0
        if (count < 1) {
            return null
        }
    }

    // 判斷是否為九蓮寶燈九面
    // 九面：手牌 13 張牌的分布為 1:3, 9:3, 2-8:各1
    val handTile13 = hand.allTiles.map { it.tile.withoutRed }
    val tileCounts13 = handTile13.groupingBy { it }.eachCount()
    val oneCount13 = tileCounts13[Tile.Numeric(suit, 1)] ?: 0
    val nineCount13 = tileCounts13[Tile.Numeric(suit, 9)] ?: 0

    val isChurenPoto9 = (oneCount13 == 3) && (nineCount13 == 3) &&
            (2..8).all { tileCounts13[Tile.Numeric(suit, it)] == 1 }

    return if (isChurenPoto9) {
        // 九蓮寶燈九面 - 雙倍役滿
        YakuResult.doubleYakuman(YakuType.ChurenPoto9)
    } else {
        // 一般九蓮寶燈 - 役滿
        YakuResult.yakuman(YakuType.ChurenPoto)
    }
}
