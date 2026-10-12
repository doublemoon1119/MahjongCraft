package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardMeldSearch
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import kotlin.math.max
import kotlin.math.min

/**
 * 台灣麻將規則的向聽數計算器。
 *
 * 負責根據台灣麻將的規則（標準型：5面子 + 1雀頭）分析手牌。
 */
class TaiwanShantenCalculator : ShantenCalculator {

    // 為了方便計算，將所有牌型映射到 0-33 的索引
    // 台麻雖然有花牌，但花牌不參與向聽計算（摸到即補），所以這裡只處理數牌和字牌
    /** 將 34 種計算用牌映射到計數陣列索引；花牌不納入此表。 */
    private val tileMap: Map<Tile, Int> = buildMap {
        // 萬子 1-9 (0-8)
        (1..9).forEach { put(Tile.Numeric(Tile.Suit.Character, it), it - 1) }
        // 筒子 1-9 (9-17)
        (1..9).forEach { put(Tile.Numeric(Tile.Suit.Dot, it), it + 8) }
        // 條子 1-9 (18-26)
        (1..9).forEach { put(Tile.Numeric(Tile.Suit.Bamboo, it), it + 17) }
        // 字牌 (27-33)
        put(Tile.Honor.East, 27)
        put(Tile.Honor.South, 28)
        put(Tile.Honor.West, 29)
        put(Tile.Honor.North, 30)
        put(Tile.Honor.White, 31)
        put(Tile.Honor.Green, 32)
        put(Tile.Honor.Red, 33)
    }

    /**
     * 計算給定手牌在台灣麻將規則下的向聽數。
     *
     * @param hand 待分析的玩家手牌。
     * @return 包含計算結果的 [ShantenResult]。
     */
    override fun calculate(hand: Hand): ShantenResult {
        // 統計手牌中每種牌的數量
        val counts = IntArray(34)
        hand.standingTiles.forEach { identifiedTile ->
            val tileKey = when (val tile = identifiedTile.tile) {
                is Tile.Numeric -> tile
                is Tile.Extension -> if (TaiwanTileTypes.isFlower(tile)) return@forEach else tile
                else -> tile
            }
            tileMap[tileKey]?.let { index ->
                counts[index]++
            }
        }

        // 獲取已副露的面子數
        val exposedMeldsCount = hand.exposedMelds.size

        // 計算標準型向聽數 (5面子 + 1雀頭)
        val minShanten = calculateStandardShanten(StandardMeldSearch(targetMelds = 5), counts, exposedMeldsCount)

        // 檢查是否已胡牌（向聽數 <= 0 且標準型已完成）
        if (minShanten <= 0 && isStandardCompleteHand(counts, exposedMeldsCount)) {
            return ShantenResult.Complete
        }

        // 檢查是否聽牌
        if (minShanten <= 0) {
            val winningTiles = calculateWinningTiles(counts, exposedMeldsCount)
            return ShantenResult.Tenpai(winningTiles)
        }

        // 返回 n 向聽
        return ShantenResult.NotTenpai(minShanten)
    }

    /**
     * 檢查標準型手牌是否已經完成（5面子+1雀頭）。
     *
     * @param counts 立牌的計數陣列。
     * @param exposedMeldsCount 已副露的面子數。
     * @return 如果標準型手牌已完成則為 true。
     */
    private fun isStandardCompleteHand(counts: IntArray, exposedMeldsCount: Int): Boolean {
        val targetMelds = 5 - exposedMeldsCount

        // 嘗試找雀頭
        for (i in counts.indices) {
            if (counts[i] >= 2) {
                val tempCounts = counts.copyOf()
                tempCounts[i] -= 2
                val melds = countMeldsRecursive(tempCounts, 0, 0, targetMelds)
                if (melds >= targetMelds) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * 遞迴計算能夠組成的最多面子數。
     *
     * @param counts 各牌的可用張數；遞迴時會暫時修改並還原。
     * @param index 下一個要檢查的牌索引。
     * @param currentMelds 已組成的面子數。
     * @param targetMelds 本次搜尋的目標面子數。
     * @return 在目標上限內可組成的最多面子數。
     */
    private fun countMeldsRecursive(counts: IntArray, index: Int, currentMelds: Int, targetMelds: Int): Int {
        if (currentMelds >= targetMelds || index >= 34) {
            return currentMelds
        }

        if (counts[index] == 0) {
            return countMeldsRecursive(counts, index + 1, currentMelds, targetMelds)
        }

        var best = currentMelds

        // 刻子
        if (counts[index] >= 3) {
            counts[index] -= 3
            best = max(best, countMeldsRecursive(counts, index, currentMelds + 1, targetMelds))
            counts[index] += 3
        }

        // 順子
        if (index < 27 && index % 9 < 7 && counts[index + 1] > 0 && counts[index + 2] > 0) {
            counts[index]--
            counts[index + 1]--
            counts[index + 2]--
            best = max(best, countMeldsRecursive(counts, index, currentMelds + 1, targetMelds))
            counts[index]++
            counts[index + 1]++
            counts[index + 2]++
        }

        return best
    }

    /**
     * 計算聽牌列表。
     *
     * @param counts 目前立牌的牌張計數。
     * @param exposedMeldsCount 已副露的面子數。
     * @return 加入一張牌後可完成標準型的牌種列表。
     */
    private fun calculateWinningTiles(counts: IntArray, exposedMeldsCount: Int): List<Tile> {
        val winningTiles = mutableListOf<Tile>()

        for (i in counts.indices) {
            val currentCount = counts[i]
            if (currentCount >= 4) continue

            val tempCounts = counts.copyOf()
            tempCounts[i]++

            if (isStandardCompleteHand(tempCounts, exposedMeldsCount)) {
                tileMap.entries.find { it.value == i }?.key?.let { tile ->
                    winningTiles.add(tile)
                }
            }
        }

        return winningTiles.distinct()
    }

    /**
     * 計算標準型 (5面子 + 1雀頭) 的向聽數。
     *
     * 公式：10 - (面子*2) - 搭子 - 雀頭
     *
     * @param search 這次計算使用的面子搜尋。
     * @param counts 立牌的計數陣列。
     * @param initialMelds 已副露的面子數。
     * @return 手牌完成台灣麻將標準型所需的向聽數。
     */
    private fun calculateStandardShanten(search: StandardMeldSearch, counts: IntArray, initialMelds: Int): Int {
        var minShanten = 10 // 台灣麻將的初始最大向聽數

        // 情況 A: 有雀頭
        for (i in counts.indices) {
            if (counts[i] >= 2) {
                counts[i] -= 2
                // 雀頭已定，面子搜尋的結果未扣雀頭，這裡再減去雀頭的 1
                val shanten = search.meldShanten(counts, initialMelds)
                minShanten = min(minShanten, shanten - 1)
                counts[i] += 2
            }
        }

        // 情況 B: 無雀頭（或尚未找到雀頭）
        // 雀頭數為 0，目標是凑齊 5 組面子，最後缺的雀頭視為一個搭子缺口
        val shantenNoPair = search.meldShanten(counts, initialMelds)
        minShanten = min(minShanten, shantenNoPair)

        return minShanten
    }
}
