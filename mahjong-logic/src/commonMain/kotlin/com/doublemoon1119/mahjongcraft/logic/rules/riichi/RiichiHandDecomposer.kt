package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandDecomposer
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandGroup
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.RiichiCompletionType
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.RiichiFuuro
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.RiichiHandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.RiichiJanto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.RiichiMentsu

/**
 * 麻將手牌分割器。
 *
 * 用於將手牌分割為標準形式（4 面子 + 1 雀頭）、
 * 七對子（Chiitoitsu）、或國士無雙（KokushiMusou）。
 */
object RiichiHandDecomposer {

    /**
     * 十三張么九牌（牌值為 1 或 9 的數牌，以及所有字牌）。
     */
    private val TERMINAL_HONORS = setOf(
        Tile.Numeric(Tile.Suit.Character, 1),
        Tile.Numeric(Tile.Suit.Character, 9),
        Tile.Numeric(Tile.Suit.Dot, 1),
        Tile.Numeric(Tile.Suit.Dot, 9),
        Tile.Numeric(Tile.Suit.Bamboo, 1),
        Tile.Numeric(Tile.Suit.Bamboo, 9),
        Tile.Honor.East,
        Tile.Honor.South,
        Tile.Honor.West,
        Tile.Honor.North,
        Tile.Honor.Red,
        Tile.Honor.Green,
        Tile.Honor.White,
    )

    /**
     * 分割手牌。
     *
     * 嘗試將手牌分割為以下幾種形式：
     * 1. 標準手牌（4 面子 + 1 雀頭）
     * 2. 七對子（Chiitoitsu）
     * 3. 國士無雙（KokushiMusou）
     *
     * @param handTiles 手牌（不含 [fuuro]，不含赤寶牌標記，不含 [winningTile]）。
     * @param winningTile 胡牌張
     * @param fuuro 副露（已曝光的面子）。
     * @return 分割後的手牌結構，若無法分割則返回空列表。
     */
    fun decompose(
        handTiles: List<Tile>,
        winningTile: Tile,
        fuuro: List<RiichiFuuro> = emptyList(),
    ): List<RiichiHandStructure> {
        // 標準手牌（4 面子 + 1 雀頭）
        val standard = tryDecomposeStandard(handTiles, winningTile, fuuro)
        if (standard.isNotEmpty()) {
            return standard
        }

        // 七對子（Chiitoitsu）
        val chiitoitsu = tryDecomposeChiitoitsu(handTiles, winningTile, fuuro)
        if (chiitoitsu != null) {
            return listOf(chiitoitsu)
        }

        // 國士無雙（KokushiMusou）
        val kokushiMusou = tryDecomposeKokushiMusou(handTiles, winningTile, fuuro)
        if (kokushiMusou != null) {
            return listOf(kokushiMusou)
        }

        return emptyList()
    }

    /**
     * 嘗試分割為標準手牌（4 面子 + 1 雀頭）。
     *
     * 窮舉所有可能的分割方式，並根據 [winningTile] 在牌型中的位置計算 [RiichiCompletionType]。
     *
     * @param handTiles 手牌中的立牌，不含和牌張與副露。
     * @param winningTile 本次和牌的牌張。
     * @param fuuro 已曝光的副露面子。
     * @return 所有可行的標準手牌結構列表，若無法分割則返回空列表。
     */
    private fun tryDecomposeStandard(
        handTiles: List<Tile>,
        winningTile: Tile,
        fuuro: List<RiichiFuuro>,
    ): List<RiichiHandStructure.Standard> = StandardHandDecomposer.decompose(
        handTiles = handTiles,
        winningTile = winningTile,
        exposedMeldCount = fuuro.size,
        targetMelds = 4,
    ).map { decomposition ->
        val winningGroup = decomposition.winningGroupIndex?.let(decomposition.groups::get)
        val completion = when (winningGroup) {
            null -> RiichiCompletionType.Tanki
            is StandardHandGroup.Triplet -> RiichiCompletionType.Shanpon
            is StandardHandGroup.Sequence -> {
                val rank = (winningTile as Tile.Numeric).value
                when {
                    rank == winningGroup.first.value + 1 -> RiichiCompletionType.Kanchan
                    (rank == 3 && winningGroup.first.value == 1) ||
                        (rank == 7 && winningGroup.first.value == 7) -> RiichiCompletionType.Penchan
                    else -> RiichiCompletionType.Ryanmen
                }
            }
        }
        RiichiHandStructure.Standard(
            mentsus = decomposition.groups.map { group ->
                when (group) {
                    is StandardHandGroup.Triplet -> RiichiMentsu.Kotsu(group.tile)
                    is StandardHandGroup.Sequence -> RiichiMentsu.Shuntsu(group.first)
                }
            },
            pair = RiichiJanto(decomposition.pair),
            fuuro = fuuro,
            completionType = completion,
        )
    }.distinct()

    /**
     * 嘗試分割為七對子。
     *
     * @param handTiles 手牌中的立牌，不含和牌張。
     * @param winningTile 本次和牌的牌張。
     * @param fuuro 已曝光的副露面子。
     * @return 符合七對子的結構；有副露或牌數／牌型不符時為 null。
     */
    private fun tryDecomposeChiitoitsu(
        handTiles: List<Tile>,
        winningTile: Tile,
        fuuro: List<RiichiFuuro>,
    ): RiichiHandStructure.Chiitoitsu? {
        if (fuuro.isNotEmpty()) return null // 七對子不能有副露

        val tiles = handTiles + winningTile
        if (tiles.size != 14) return null

        // 使用 groupingBy 來正確計算相同牌的數量
        val tileCounts = tiles.groupingBy { it }.eachCount()

        val pairs = mutableListOf<RiichiJanto>()
        for ((tile, count) in tileCounts) {
            if (count >= 2) {
                pairs.add(RiichiJanto(tile))
            }
        }

        // 需要剛好 7 個對子
        val uniquePairs = pairs.distinctBy { it.tile }
        if (uniquePairs.size != 7) return null

        // 驗證每張牌都是對子
        for (pair in uniquePairs) {
            if ((tileCounts[pair.tile] ?: 0) != 2) return null
        }

        return RiichiHandStructure.Chiitoitsu(pairs = uniquePairs)
    }

    /**
     * 嘗試分割為國士無雙。
     *
     * @param handTiles 手牌中的立牌，不含和牌張。
     * @param winningTile 本次和牌的牌張。
     * @param fuuro 已曝光的副露面子。
     * @return 符合國士無雙的結構；有副露或牌型不符時為 null。
     */
    private fun tryDecomposeKokushiMusou(
        handTiles: List<Tile>,
        winningTile: Tile,
        fuuro: List<RiichiFuuro>,
    ): RiichiHandStructure.KokushiMusou? {
        if (fuuro.isNotEmpty()) return null // 國士無雙不能有副露

        val tiles = handTiles + winningTile
        if (tiles.size != 14) return null

        // 檢查是否有十三張不同的么九牌
        val uniqueTiles = tiles.toSet()

        // 檢查是否涵蓋所有十三張么九牌
        val hasAllOrphans = TERMINAL_HONORS.all { it in uniqueTiles }
        if (!hasAllOrphans) return null

        // 找到可以做雀頭的牌（重複的牌）
        val tileCounts = tiles.groupingBy { it }.eachCount()
        val headCandidates = tileCounts.filter { it.value >= 2 }.keys

        if (headCandidates.isEmpty()) return null

        // 選擇第一個可以做雀頭的牌
        val headTile = headCandidates.first()

        // 移除一張雀頭牌，保留其餘 13 張作為孤張
        val orphans = tiles.toMutableList()
        orphans.remove(headTile)

        if (orphans.size != 13) return null

        return RiichiHandStructure.KokushiMusou(
            orphans = orphans,
            headTile = headTile,
        )
    }
}
