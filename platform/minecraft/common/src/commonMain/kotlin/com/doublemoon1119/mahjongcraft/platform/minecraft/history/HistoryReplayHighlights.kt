package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayDiscardDto

/** 牌譜回放場景中發光的牌，判斷方式與對局中相同。 */
object HistoryReplayHighlights {
    /**
     * 最後打出、仍留在牌河的那張牌。與對局中相同，這張牌被鳴走或和走、這一局已經結束，或還沒有人打牌時沒有。
     *
     * @param discardOrder 全桌依打出先後排列的捨牌索引，包含已被鳴走的牌。
     * @param discards 各家牌河中的牌。
     * @param roundOver 這一局是否已經結束。
     * @return 局內牌索引。沒有時為 null。
     */
    fun latestDiscard(discardOrder: List<Int>, discards: List<HistoryReplayDiscardDto>, roundOver: Boolean): Int? {
        if (roundOver) return null
        val latest = discardOrder.lastOrNull() ?: return null
        return latest.takeIf { index -> discards.any { it.tile == index && !it.isTaken } }
    }

    /**
     * 與指著的牌相同的其他牌。與對局中相同，依規則把牌換成標準牌面後比較，例如赤五與一般的五相同。
     *
     * @param T 標準牌面的型別。
     * @param target 指著的牌的局內牌索引。
     * @param candidates 可以一起發光的牌，例如看得到牌面的牌。
     * @param canonical 局內牌索引對應的標準牌面。看不到牌面時為 null。
     * @return 相同的其他牌，不含指著的牌。指著的牌看不到牌面時為空。
     */
    fun <T : Any> matching(target: Int, candidates: Set<Int>, canonical: (Int) -> T?): Set<Int> {
        val face = canonical(target) ?: return emptySet()
        return candidates.filter { it != target && canonical(it) == face }.toSet()
    }
}
