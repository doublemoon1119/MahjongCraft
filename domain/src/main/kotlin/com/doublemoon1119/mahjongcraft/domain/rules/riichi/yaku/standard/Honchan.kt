package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.util.isTerminal

/**
 * 混全帶么九 (Honchan) 役種檢測器。
 *
 * 混全帶么九是立直麻將中的役種，條件如下：
 * - 門前清：2 翻
 * - 副露：1 翻
 *
 * 手牌的所有面子（順子、刻子、槓）和雀頭都必須包含么九牌（1、9 數牌）或字牌。
 * 例如：每個面子都至少包含一張老頭牌或字牌。
 *
 * @param handStructure 手牌結構（由 [com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandDecomposer] 分割後的結果）。
 * @param isMenzen 是否為門前清（門前清則為 2 翻，否則為 1 翻）。
 * @return 混全帶么九役種結果，若不符合則返回 null。
 */
fun calculateHonchan(
    handStructure: HandStructure?,
    isMenzen: Boolean
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 收集所有面子（手牌 + 副露）
    val allMentsus = standard.mentsus + standard.fuuro.map { it.mentsu }

    // 檢查每個面子是否包含么九牌或字牌
    for (mentsu in allMentsus) {
        val tiles = mentsu.tiles
        val hasTerminalOrHonor = tiles.any { tile ->
            when (tile) {
                is Tile.Numeric -> tile.isTerminal
                is Tile.Honor -> true
                else -> false
            }
        }
        if (!hasTerminalOrHonor) {
            return null
        }
    }

    // 檢查雀頭是否包含么九牌或字牌
    val pairTiles = listOf(standard.pair.tile, standard.pair.tile)
    val pairHasTerminalOrHonor = pairTiles.any { tile ->
        when (tile) {
            is Tile.Numeric -> tile.isTerminal
            is Tile.Honor -> true
            else -> false
        }
    }
    if (!pairHasTerminalOrHonor) {
        return null
    }

    // 不包含任何字牌，是純全帶么九
    if (allMentsus.none { it.tiles.any { tile -> tile is Tile.Honor } }){
        return null
    }

    val han = if (isMenzen) 2 else 1
    return YakuResult.han(YakuType.Honchan, han)
}