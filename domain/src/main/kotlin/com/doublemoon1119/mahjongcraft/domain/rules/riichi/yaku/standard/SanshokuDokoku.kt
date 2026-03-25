package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType

/**
 * 三色同刻 (Sanshoku Dokoku) 役種檢測器。
 *
 * 三色同刻是立直麻將中的二翻役，條件如下：
 * 手牌中含有三組相同數字但不同花色的刻子（或槓）。
 * 例如：111m、111p、111s。
 *
 * 刻子可以來自手牌或副露（鳴牌）。
 *
 * @param handStructure 手牌結構（由 [com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandDecomposer] 分割後的結果）。
 * @return 三色同刻役種結果，若不符合則返回 null。
 */
fun calculateSanshokuDokoku(
    handStructure: HandStructure?
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 收集所有的面子（包括手牌中的面子與副露）
    val allMentsus = standard.mentsus + standard.fuuro.map { it.mentsu }

    // 收集所有刻子/槓的牌
    val ponTiles = mutableListOf<Tile.Numeric>()

    for (mentsu in allMentsus) {
        val tile = when (mentsu) {
            is Mentsu.Kotsu -> mentsu.tile
            is Mentsu.Ankan -> mentsu.tile
            is Mentsu.Minkan -> mentsu.tile
            is Mentsu.Kakan -> mentsu.tile
            else -> continue
        }
        if (tile is Tile.Numeric) {
            ponTiles.add(tile)
        }
    }

    // 檢查是否存在三個相同數字但不同花色的刻子
    val groupedByValue = ponTiles.groupBy { it.value }

    for ((_, tilesInGroup) in groupedByValue) {
        val suits = tilesInGroup.map { it.suit }.toSet()
        if (suits.size >= 3) {
            return YakuResult.han(YakuType.SanshokuDokoku, 2)
        }
    }

    return null
}
