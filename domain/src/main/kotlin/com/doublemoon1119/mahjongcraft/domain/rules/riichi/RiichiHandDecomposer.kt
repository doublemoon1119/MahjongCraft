package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Fuuro
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Janto
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Mentsu
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
     * @param tiles 手牌（不含副露，不含赤寶牌標記）。
     * @param fuuro 副露（已曝光的面子）。
     * @return 分割後的手牌結構，若無法分割則返回 null。
     */
    fun decompose(
        tiles: List<Tile>,
        fuuro: List<Fuuro> = emptyList()
    ): HandStructure? {
        val normalizedTiles = tiles.map { it.withoutRed }

        return tryDecomposeStandard(normalizedTiles, fuuro)
            ?: tryDecomposeChiitoitsu(normalizedTiles, fuuro)
            ?: tryDecomposeKokushiMusou(normalizedTiles)
    }

    /**
     * 嘗試分割為標準手牌（4 面子 + 1 雀頭）。
     */
    private fun tryDecomposeStandard(
        tiles: List<Tile>,
        fuuro: List<Fuuro>
    ): HandStructure.Standard? {
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
        if (currentHandSize != correctHandSize) return null

        val fuuroCount = fuuro.size

        // 建立 TileCountMap，只包含手牌（不含 fuuro）
        val tileCounts = mutableMapOf<Tile, Int>()
        for (tile in tiles) {
            tileCounts[tile] = (tileCounts[tile] ?: 0) + 1
        }

        // 嘗試每個可能的雀頭（雀頭必須來自手牌）
        for ((headTile, count) in tileCounts) {
            if (count >= 2) {
                // 複製並移除雀頭
                val remainingCounts = tileCounts.toMutableMap()
                remainingCounts[headTile] = count - 2
                if (remainingCounts[headTile] == 0) {
                    remainingCounts.remove(headTile)
                }

                // 副露貢獻 fuuroCount 個面子，門清部分需要 4 - fuuroCount 個面子
                val requiredMentsus = 4 - fuuroCount
                val mentsus = tryFindMentsusFromCounts(remainingCounts, requiredMentsus)
                if (mentsus != null && mentsus.size == requiredMentsus) {
                    return HandStructure.Standard(
                        mentsus = mentsus,
                        pair = Janto(headTile),
                        fuuro = fuuro
                    )
                }
            }
        }

        return null
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

            val result = tryFindMentsusFromCounts(newCounts, requiredMentsus, currentMentsus.apply { add(Mentsu.Kotsu(tile)) })
            if (result != null) return result
            currentMentsus.removeLast()
        }

        // 嘗試找暗槓（4 張相同）
        if (count >= 4 && (tile is Tile.Numeric || tile is Tile.Honor)) {
            val newCounts = tileCounts.toMutableMap()
            newCounts[tile] = count - 4
            if (newCounts[tile] == 0) newCounts.remove(tile)

            val result = tryFindMentsusFromCounts(newCounts, requiredMentsus, currentMentsus.apply { add(Mentsu.Ankan(tile)) })
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

                val result = tryFindMentsusFromCounts(newCounts, requiredMentsus, currentMentsus.apply { add(Mentsu.Shuntsu(tile)) })
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
        tiles: List<Tile>,
        fuuro: List<Fuuro>
    ): HandStructure.Chiitoitsu? {
        if (fuuro.isNotEmpty()) return null // 七對子不能有副露

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
    private fun tryDecomposeKokushiMusou(tiles: List<Tile>): HandStructure.KokushiMusou? {
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
