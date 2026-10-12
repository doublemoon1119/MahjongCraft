package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardMeldSearch
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import kotlin.math.max
import kotlin.math.min

/**
 * 立直麻將規則的向聽數計算器。
 *
 * 負責根據立直麻將的規則（包含標準型、七對子、國士無雙）分析手牌。
 */
class RiichiShantenCalculator : ShantenCalculator {

    /** 三十四種標準牌到計數陣列索引的對照，赤牌先正規化後查詢。 */
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
     * 計算給定手牌在立直麻將規則下的向聽數。
     *
     * @param hand 待分析的玩家手牌。
     * @return 包含計算結果的 [ShantenResult]。
     */
    override fun calculate(hand: Hand): ShantenResult {
        // 統計手牌中每種牌的數量
        val counts = IntArray(34)
        hand.standingTiles.forEach { identifiedTile ->
            // 忽略赤寶牌標記，將其視為普通牌處理
            val tileKey = identifiedTile.tile.riichiCanonical
            tileMap[tileKey]?.let { index ->
                counts[index]++
            }
        }

        // 獲取已副露的面子數
        val exposedMeldsCount = hand.exposedMelds.size
        val search = StandardMeldSearch(targetMelds = 4)

        // 計算標準型向聽數 (4面子 + 1雀頭)
        val standardShanten = calculateStandardShanten(search, counts, exposedMeldsCount)

        // 計算七對子向聽數 (七對子必須門前清，即 exposedMeldsCount == 0)
        var sevenPairsShanten = 8
        if (exposedMeldsCount == 0) {
            sevenPairsShanten = calculateSevenPairsShanten(counts)
        }

        // 計算國士無雙向聽數 (國士無雙必須門前清)
        var kokushiShanten = 8
        if (exposedMeldsCount == 0) {
            kokushiShanten = calculateKokushiShanten(counts)
        }

        // 取最小值
        val minShanten = min(standardShanten, min(sevenPairsShanten, kokushiShanten))

        // 檢查是否已胡牌（向聽數 <= 0 且標準型已完成）
        if (minShanten <= 0 && isStandardCompleteHand(search, counts, exposedMeldsCount)) {
            return ShantenResult.Complete
        }

        // 檢查是否已胡牌（七對子／國士無雙），直接複用上方已算出的結果，避免重複計算
        if (sevenPairsShanten == -1 || kokushiShanten == -1) {
            return ShantenResult.Complete
        }

        // 檢查是否聽牌
        if (minShanten <= 0) {
            val winningTiles = calculateWinningTiles(search, counts, exposedMeldsCount)
            return ShantenResult.Tenpai(winningTiles)
        }

        // 返回 n 向聽
        return ShantenResult.NotTenpai(minShanten)
    }

    /**
     * 計算聽牌列表。
     *
     * 依三十四種標準牌的索引順序試加一張，判斷是否完成和牌型；立牌已有四張的牌種略過。
     * 此處不檢查役種、振聽或場上剩餘張數。
     *
     * @param search 這次計算使用的面子搜尋。
     * @param counts 立牌的計數陣列。
     * @param exposedMeldsCount 已副露的面子數。
     * @return 可以胡的牌列表。
     */
    private fun calculateWinningTiles(search: StandardMeldSearch, counts: IntArray, exposedMeldsCount: Int): List<Tile> {
        val winningTiles = mutableListOf<Tile>()

        // 嘗試每種牌作為進張
        for (i in counts.indices) {
            // 立牌已有四張的牌種不能再試加
            val currentCount = counts[i]
            if (currentCount >= 4) continue // 手中已有 4 張，無法再摸

            // 模擬摸進這張牌
            val tempCounts = counts.copyOf()
            tempCounts[i]++

            // 檢查是否可以胡牌
            if (canWin(search, tempCounts, exposedMeldsCount)) {
                tileMap.entries.find { it.value == i }?.key?.let { tile ->
                    winningTiles.add(tile)
                }
            }
        }

        return winningTiles.distinct()
    }

    /**
     * 檢查給定的牌型是否已經胡牌。
     *
     * @param search 這次計算使用的面子搜尋。
     * @param counts 立牌的計數陣列。
     * @param exposedMeldsCount 已副露的面子數。
     * @return 如果已經胡牌則為 true。
     */
    private fun canWin(search: StandardMeldSearch, counts: IntArray, exposedMeldsCount: Int): Boolean {
        // 檢查標準型
        if (isStandardCompleteHand(search, counts, exposedMeldsCount)) {
            return true
        }

        // 檢查七對子
        if (exposedMeldsCount == 0 && calculateSevenPairsShanten(counts) == -1) {
            return true
        }

        // 檢查國士無雙
        if (exposedMeldsCount == 0 && calculateKokushiShanten(counts) == -1) {
            return true
        }

        return false
    }

    /**
     * 檢查標準型手牌是否已經完成（4面子+1雀頭）。
     *
     * 找得到雀頭與所需的面子數即成立，允許有不屬於任何面子的剩餘牌。
     *
     * @param search 這次計算使用的面子搜尋。
     * @param counts 立牌的計數陣列。
     * @param exposedMeldsCount 已副露的面子數。
     * @return 如果標準型手牌已完成則為 true。
     */
    private fun isStandardCompleteHand(search: StandardMeldSearch, counts: IntArray, exposedMeldsCount: Int): Boolean {
        val targetMelds = 4 - exposedMeldsCount

        // 嘗試找雀頭
        for (i in counts.indices) {
            if (counts[i] >= 2) {
                counts[i] -= 2
                val melds = search.maxMelds(counts)
                counts[i] += 2
                if (melds >= targetMelds) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * 計算標準型 (4面子 + 1雀頭) 的向聽數。
     *
     * @param search 這次計算使用的面子搜尋。
     * @param counts 立牌的計數陣列。
     * @param initialMelds 已副露的面子數。
     * @return 所有雀頭選擇與無雀頭情況中的最小標準型向聽數。
     */
    private fun calculateStandardShanten(search: StandardMeldSearch, counts: IntArray, initialMelds: Int): Int {
        var minShanten = 8 // 立直麻將的初始最大向聽數

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
        // 雀頭數為 0，目標是凑齊 4 組面子，最後缺的雀頭視為一個搭子缺口
        val shantenNoPair = search.meldShanten(counts, initialMelds)
        minShanten = min(minShanten, shantenNoPair)

        return minShanten
    }

    /**
     * 計算七對子 (Seven Pairs) 的向聽數。
     * 規則：必須有 7 個不同的對子。
     * 向聽數 = 6 - 對子數 + max(0, 7 - 牌種類數)
     * 若已湊齊 7 對子，返回 -1 表示完成牌型。
     *
     * @param counts 三十四種標準牌的立牌計數。
     * @return 七對子向聽數，完成牌型時為 -1。
     */
    private fun calculateSevenPairsShanten(counts: IntArray): Int {
        var pairs = 0
        var kinds = 0 // 有幾種牌

        for (count in counts) {
            if (count > 0) {
                kinds++
                if (count >= 2) {
                    pairs++
                }
            }
        }

        // 七對子向聽數公式：
        // 基本值：6 - pairs
        // 如果牌種類不足 7 種，需要額外補張
        // 補張數 = 7 - kinds
        // 所以總向聽數 = 6 - pairs + max(0, 7 - kinds)
        // 當 pairs == 7 且 kinds == 7 時，返回 -1（胡牌）

        val shanten = 6 - pairs + max(0, 7 - kinds)
        return if (shanten == 0 && pairs == 7 && kinds == 7) -1 else shanten
    }

    /**
     * 計算國士無雙 (Thirteen Orphans) 的向聽數。
     * 規則：13 種么九牌各一張，其中一種有兩張。
     * 向聽數 = 13 - (現有的么九牌種類數) - (是否有任何一種么九牌 >= 2 ? 1 : 0)
     * 若已湊齊 13 種么九牌且有雀頭，返回 -1 表示完成牌型。
     *
     * @param counts 三十四種標準牌的立牌計數。
     * @return 國士無雙向聽數，完成牌型時為 -1。
     */
    private fun calculateKokushiShanten(counts: IntArray): Int {
        // 么九牌的索引列表: 1,9m (0,8), 1,9p (9,17), 1,9s (18,26), z (27-33)
        val termIndices = intArrayOf(
            0, 8, // 1m, 9m
            9, 17, // 1p, 9p
            18, 26, // 1s, 9s
            27, 28, 29, 30, 31, 32, 33, // 東南西北白發中
        )

        var yaochuTypes = 0
        var hasPair = false

        for (index in termIndices) {
            if (counts[index] > 0) {
                yaochuTypes++
                if (counts[index] >= 2) {
                    hasPair = true
                }
            }
        }

        // 公式：13 - 么九牌種類數 - (如果有雀頭 ? 1 : 0)
        // 當 yaochuTypes == 13 且 hasPair == true 時，返回 -1（胡牌）

        val shanten = 13 - yaochuTypes - (if (hasPair) 1 else 0)
        return if (shanten == 0 && yaochuTypes == 13 && hasPair) -1 else shanten
    }
}
