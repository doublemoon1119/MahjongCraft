package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.*
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed

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
        Tile.Honor.White
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
        fuuro: List<Fuuro> = emptyList()
    ): List<HandStructure> {
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
     * 窮舉所有可能的分割方式，並根據 [winningTile] 在牌型中的位置計算 [CompletionType]。
     *
     * @return 所有可行的標準手牌結構列表，若無法分割則返回空列表。
     */
    private fun tryDecomposeStandard(
        handTiles: List<Tile>,
        winningTile: Tile,
        fuuro: List<Fuuro>
    ): List<HandStructure.Standard> {
        val tiles = handTiles.map { it.withoutRed } + winningTile.withoutRed

        // 計算副露的牌張總數（碰為 3 張，槓為 4 張）
        val fuuroTilesAmount = fuuro.sumOf { it.mentsu.tiles.size }
        // 計算副露中的槓數量（暗槓、明槓、加槓均計為槓）
        val fuuroKanAmount = fuuro.count {
            it.mentsu is Mentsu.Ankan ||
                    it.mentsu is Mentsu.Minkan ||
                    it.mentsu is Mentsu.Kakan
        }
        // 當前手牌總張數（手牌 + 副露牌張）
        val currentHandSize = tiles.size + fuuroTilesAmount
        // 正確的手牌張數：標準為 14 張，每多一個槓多 1 張
        val correctHandSize = 14 + fuuroKanAmount
        if (currentHandSize != correctHandSize) return emptyList()

        val fuuroCount = fuuro.size

        // 建立 TileCountMap，只包含手牌（不含 fuuro）
        // 排序確保遞迴時的一致性：數牌先於字牌，數牌依花色與數值排序
        val sortedTiles = tiles.sortedWith(
            compareBy(
                { it !is Tile.Numeric }, // 1. 數牌排在前面（false < true）
                { (it as? Tile.Numeric)?.suit },    // 2. 數牌再按花色排序
                { (it as? Tile.Numeric)?.value }    // 3. 數牌再按數值排序
            ))
        val tileCounts = mutableMapOf<Tile, Int>()
        for (tile in sortedTiles) {
            tileCounts[tile] = (tileCounts[tile] ?: 0) + 1
        }

        val results = mutableListOf<HandStructure.Standard>()
        val winTile = winningTile.withoutRed
        val winTileCount = tileCounts[winTile] ?: 0

        // 副露貢獻 fuuroCount 個面子，門清部分需要 4 - fuuroCount 個面子
        val requiredMentsus = 4 - fuuroCount

        // 嘗試每個可能的雀頭
        for ((pairTile, pairCount) in tileCounts) {
            if (pairCount >= 2) {
                // 複製並移除雀頭
                val remainingCounts = tileCounts.toMutableMap()
                remainingCounts[pairTile] = pairCount - 2
                if (remainingCounts[pairTile] == 0) {
                    remainingCounts.remove(pairTile)
                }

                // 計算可能的 completionType
                val possibleMestsuCompletions =
                    computePossibleMenstuCompletions(winTile, winTileCount, pairTile, remainingCounts)

                // 遍歷 possibleMestsuCompletions
                for ((completionType, completionMentsu) in possibleMestsuCompletions) {
                    // 單騎的情況
                    if (completionType is CompletionType.Tanki || completionMentsu == null) {
                        // 需要找 requiredMentsus 個面子
                        val mentsus = tryFindMentsusFromCounts(remainingCounts, requiredMentsus)
                        if (mentsus != null && mentsus.size == requiredMentsus) {
                            results.add(
                                HandStructure.Standard(
                                    mentsus = mentsus,
                                    pair = Janto(pairTile),
                                    fuuro = fuuro,
                                    completionType = CompletionType.Tanki
                                )
                            )
                        }
                        continue
                    }

                    // 複製 remainingCounts 並移除 completionMentsu
                    val remainingCountsAfterCompletion = remainingCounts.toMutableMap()
                    for (tile in completionMentsu.tiles){
                        val count = remainingCountsAfterCompletion[tile] ?: continue
                        remainingCountsAfterCompletion[tile] = count - 1
                        if (remainingCountsAfterCompletion[tile] == 0) {
                            remainingCountsAfterCompletion.remove(tile)
                        }
                    }

                    // winningTile 已構成一個面子，只需要找 requiredMentsus - 1 個面子
                    val mentsus = tryFindMentsusFromCounts(remainingCountsAfterCompletion, requiredMentsus - 1)
                    if (mentsus != null && mentsus.size == requiredMentsus - 1) {
                        // 將 winningTile 構成的面子加入面子列表
                        val allMentsus = mentsus + completionMentsu
                        results.add(
                            HandStructure.Standard(
                                mentsus = allMentsus,
                                pair = Janto(pairTile),
                                fuuro = fuuro,
                                completionType = completionType
                            )
                        )
                    }
                }
            }
        }

        return results.distinctBy { it.completionType to it.pair.tile }
    }

    /**
     * 根據 [winTile] 與雀頭 [pairTile] 的關係，計算可能的 [CompletionType] 和 [Mentsu]。
     *
     * [winTile] 在牌型中有五種位置：
     * - 單騎 (Tanki)：[winTile] 做雀頭（需要 2 張以上）
     * - 雙碰 (Shanpon)：[winTile] 做刻子（不是雀頭，需要 3 張以上）
     * - 嵌張 (Kanchan)：[winTile] 在順子中間
     * - 邊張 (Penchan)：[winTile] 在順子邊緣
     * - 兩面 (Ryanmen)：[winTile] 在順子兩頭
     *
     * @param tileCounts 剩餘牌張計數（用於檢查構成順子所需的相鄰牌是否存在）
     * @return [CompletionType] 以及對應的 [Mentsu] (單騎的話 [Mentsu] 為 `null`)
     */
    private fun computePossibleMenstuCompletions(
        winTile: Tile,
        winTileCount: Int,
        pairTile: Tile,
        tileCounts: Map<Tile, Int>
    ): List<Pair<CompletionType, Mentsu?>> {
        val completion = mutableListOf<Pair<CompletionType, Mentsu?>>()

        // winningTile 做雀頭 (至少 2 張) → 單騎
        if (pairTile == winTile && winTileCount >= 2) {
            completion.add(CompletionType.Tanki to null)
        }

        // winningTile 做刻子（至少 3 張）但不是雀頭 → 雙碰
        if (pairTile != winTile && winTileCount >= 3) {
            completion.add(CompletionType.Shanpon to Mentsu.Kotsu(winTile))
        }

        // winningTile 構成順子 → 嵌張、邊張、兩面
        if (pairTile != winTile && winTile is Tile.Numeric) {
            val value = winTile.value

            // 嵌張：winningTile 在順子中間 (2-8)，需要 (value-1) 和 (value+1)
            if (value in 2..8) {
                val prevTile = Tile.Numeric(winTile.suit, value - 1)
                val nextTile = Tile.Numeric(winTile.suit, value + 1)
                val prevCount = tileCounts[prevTile] ?: 0
                val nextCount = tileCounts[nextTile] ?: 0
                if (prevCount >= 1 && nextCount >= 1 && winTileCount >= 1) {
                    completion.add(CompletionType.Kanchan to Mentsu.Shuntsu(prevTile))
                }
            }

            // 邊張：winningTile 在順子邊緣
            if (value == 3) {
                // 12 聽 3，需要 1,2
                val prevTile = Tile.Numeric(winTile.suit, 1)
                val prevPrevTile = Tile.Numeric(winTile.suit, 2)
                val prevCount = tileCounts[prevTile] ?: 0
                val prevPrevCount = tileCounts[prevPrevTile] ?: 0
                if (prevCount >= 1 && prevPrevCount >= 1 && winTileCount >= 1) {
                    completion.add(CompletionType.Penchan to Mentsu.Shuntsu(prevTile))
                }
            }
            if (value == 7) {
                // 89 聽 7，需要 8,9
                val nextTile = Tile.Numeric(winTile.suit, 8)
                val nextNextTile = Tile.Numeric(winTile.suit, 9)
                val nextCount = tileCounts[nextTile] ?: 0
                val nextNextCount = tileCounts[nextNextTile] ?: 0
                if (nextCount >= 1 && nextNextCount >= 1 && winTileCount >= 1) {
                    completion.add(CompletionType.Penchan to Mentsu.Shuntsu(winTile))
                }
            }

            // 兩面：winningTile 在順子兩頭，需要檢查相鄰牌是否存在
            if (value in 1..9 && winTileCount >= 1) {
                // 情況 1：winningTile 為順子小端 (1-6)，如 23 聽 1，需要 2,3
                if (value in 1..6) {
                    val next1 = Tile.Numeric(winTile.suit, value + 1)
                    val next2 = Tile.Numeric(winTile.suit, value + 2)
                    val c1 = tileCounts[next1] ?: 0
                    val c2 = tileCounts[next2] ?: 0
                    if (c1 >= 1 && c2 >= 1) {
                        completion.add(CompletionType.Ryanmen to Mentsu.Shuntsu(winTile))
                    }
                }

                // 情況 2：winningTile 為順子大端 (3-8)，如 23 聽 4，需要 1,2
                if (value in 4..9) {
                    val prev1 = Tile.Numeric(winTile.suit, value - 1)
                    val prev2 = Tile.Numeric(winTile.suit, value - 2)
                    val c1 = tileCounts[prev1] ?: 0
                    val c2 = tileCounts[prev2] ?: 0
                    if (c1 >= 1 && c2 >= 1) {
                        completion.add(CompletionType.Ryanmen to Mentsu.Shuntsu(prev2))
                    }
                }
            }
        }

        return completion.distinct()
    }

    /**
     * 從 TileCountMap 遞迴尋找指定數量的面子。
     */
    private fun tryFindMentsusFromCounts(
        tileCounts: Map<Tile, Int>,
        requiredMentsus: Int,
        currentMentsus: MutableList<Mentsu> = mutableListOf()
    ): List<Mentsu>? {
        if (tileCounts.isEmpty() && currentMentsus.size == requiredMentsus) {
            return currentMentsus.toList()
        }
        if (tileCounts.isEmpty()) {
            return null
        }
        if (currentMentsus.size > requiredMentsus) {
            return null
        }

        // 取得第一張牌
        val tile = tileCounts.keys.firstOrNull() ?: return null
        val count = tileCounts[tile] ?: 0

        if (count == 0) return null

        // 嘗試找刻子（3 張相同）- 僅限於數牌或字牌
        if (count >= 3 && (tile is Tile.Numeric || tile is Tile.Honor)) {
            val newCounts = tileCounts.toMutableMap()
            newCounts[tile] = count - 3
            if (newCounts[tile] == 0) newCounts.remove(tile)

            val result =
                tryFindMentsusFromCounts(newCounts, requiredMentsus, currentMentsus.apply { add(Mentsu.Kotsu(tile)) })
            if (result != null) return result
            currentMentsus.removeLast()
        }

        // 嘗試找暗槓（4 張相同）
        if (count >= 4 && (tile is Tile.Numeric || tile is Tile.Honor)) {
            val newCounts = tileCounts.toMutableMap()
            newCounts[tile] = count - 4
            if (newCounts[tile] == 0) newCounts.remove(tile)

            val result =
                tryFindMentsusFromCounts(newCounts, requiredMentsus, currentMentsus.apply { add(Mentsu.Ankan(tile)) })
            if (result != null) return result
            currentMentsus.removeLast()
        }

        // 嘗試找順子（僅限數牌，牌值 1-7）
        if (tile is Tile.Numeric && tile.value in 1..7) {
            val next1 = Tile.Numeric(tile.suit, tile.value + 1)
            val next2 = Tile.Numeric(tile.suit, tile.value + 2)
            val count1 = tileCounts[next1] ?: 0
            val count2 = tileCounts[next2] ?: 0

            if (count1 >= 1 && count2 >= 1) {
                val newCounts = tileCounts.toMutableMap()
                newCounts[tile] = count - 1
                if (newCounts[tile] == 0) newCounts.remove(tile)

                newCounts[next1] = count1 - 1
                if (newCounts[next1] == 0) newCounts.remove(next1)

                newCounts[next2] = count2 - 1
                if (newCounts[next2] == 0) newCounts.remove(next2)

                val result = tryFindMentsusFromCounts(
                    newCounts,
                    requiredMentsus,
                    currentMentsus.apply { add(Mentsu.Shuntsu(tile)) })
                if (result != null) return result
                currentMentsus.removeLast()
            }
        }

        return null
    }

    /**
     * 嘗試分割為七對子。
     */
    private fun tryDecomposeChiitoitsu(
        handTiles: List<Tile>,
        winningTile: Tile,
        fuuro: List<Fuuro>
    ): HandStructure.Chiitoitsu? {
        if (fuuro.isNotEmpty()) return null // 七對子不能有副露

        val tiles = handTiles.map { it.withoutRed } + winningTile.withoutRed
        if (tiles.size != 14) return null

        // 使用 groupingBy 來正確計算相同牌的數量
        val tileCounts = tiles.groupingBy { it }.eachCount()

        val pairs = mutableListOf<Janto>()
        for ((tile, count) in tileCounts) {
            if (count >= 2) {
                pairs.add(Janto(tile))
            }
        }

        // 需要剛好 7 個對子
        val uniquePairs = pairs.distinctBy { it.tile }
        if (uniquePairs.size != 7) return null

        // 驗證每張牌都是對子
        for (pair in uniquePairs) {
            if ((tileCounts[pair.tile] ?: 0) != 2) return null
        }

        return HandStructure.Chiitoitsu(pairs = uniquePairs)
    }

    /**
     * 嘗試分割為國士無雙。
     */
    private fun tryDecomposeKokushiMusou(
        handTiles: List<Tile>,
        winningTile: Tile,
        fuuro: List<Fuuro>
    ): HandStructure.KokushiMusou? {
        if (fuuro.isNotEmpty()) return null // 國士無雙不能有副露

        val tiles = handTiles.map { it.withoutRed } + winningTile.withoutRed
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

        return HandStructure.KokushiMusou(
            orphans = orphans,
            headTile = headTile
        )
    }
}
