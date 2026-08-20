package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandDecomposer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/**
 * 三色同順 (Sanshoku Doujun) 役種檢測器。
 *
 * 三色同順是立直麻將中的役種，條件如下：
 * - 門前清：2 翻
 * - 副露：1 翻
 *
 * 手牌中含有三組相同數字但不同花色的順子。
 * 例如：123m、123p、123s。
 *
 * 順子可以來自手牌或副露（鳴牌）。
 *
 * @param handStructure 手牌結構（由 [RiichiHandDecomposer] 分割後的結果）。
 * @param isMenzen 是否為門前清（門前清則為 2 翻，否則為 1 翻）。
 * @return 三色同順役種結果，若不符合則返回 null。
 */
fun calculateSanshokuDoujun(
    handStructure: HandStructure,
    isMenzen: Boolean,
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 收集所有的面子（包括手牌中的面子與副露）
    val allMentsus = standard.mentsus + standard.fuuro.map { it.mentsu }

    // 收集所有順子的牌
    val shuntsuTiles = mutableListOf<Tile.Numeric>()

    for (mentsu in allMentsus) {
        if (mentsu is Mentsu.Shuntsu) {
            val tile = mentsu.headTile
            if (tile is Tile.Numeric) {
                shuntsuTiles.add(tile)
            }
        }
    }

    // 檢查是否存在三個相同數字但不同花色的順子
    val groupedByValue = shuntsuTiles.groupBy { it.value }

    for ((_, tilesInGroup) in groupedByValue) {
        val suits = tilesInGroup.map { it.suit }.toSet()
        if (suits.size >= 3) {
            val han = if (isMenzen) 2 else 1
            return YakuResult.han(YakuType.SanshokuDoujun, han)
        }
    }

    return null
}
