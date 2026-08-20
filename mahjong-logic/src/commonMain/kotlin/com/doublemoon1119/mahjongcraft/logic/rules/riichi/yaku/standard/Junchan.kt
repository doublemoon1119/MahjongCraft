package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandDecomposer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.util.isTerminal

/**
 * 純全帶么九 (Junchan) 役種檢測器。
 *
 * 純全帶么九是立直麻將中的役種，條件如下：
 * - 門前清：3 翻
 * - 副露：2 翻
 *
 * 手牌的所有面子（順子、刻子、槓）和雀頭都必須包含老頭牌（1、9 數牌）。
 * 與混全帶么九不同，純全帶么九不能包含任何字牌。
 *
 * @param handStructure 手牌結構（由 [RiichiHandDecomposer] 分割後的結果）。
 * @param isMenzen 是否為門前清（門前清則為 3 翻，否則為 2 翻）。
 * @return 純全帶么九役種結果，若不符合則返回 null。
 */
fun calculateJunchan(
    handStructure: HandStructure,
    isMenzen: Boolean,
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 收集所有面子（手牌 + 副露）
    val allMentsus = standard.mentsus + standard.fuuro.map { it.mentsu }

    // 檢查每個面子是否包含老頭牌（不能有字牌）
    for (mentsu in allMentsus) {
        val tiles = mentsu.tiles
        // 必須包含老頭牌
        val hasTerminal = tiles.any { tile -> tile is Tile.Numeric && tile.isTerminal }
        // 不能有字牌
        val hasHonor = tiles.any { tile -> tile is Tile.Honor }

        if (!hasTerminal || hasHonor) {
            return null
        }
    }

    // 檢查雀頭是否包含老頭牌（不能有字牌）
    val pairTiles = listOf(standard.pair.tile, standard.pair.tile)
    val pairHasTerminal = pairTiles.any { tile -> tile is Tile.Numeric && tile.isTerminal }
    val pairHasHonor = pairTiles.any { tile -> tile is Tile.Honor }

    if (!pairHasTerminal || pairHasHonor) {
        return null
    }

    val han = if (isMenzen) 3 else 2
    return YakuResult.han(YakuType.Junchan, han)
}
