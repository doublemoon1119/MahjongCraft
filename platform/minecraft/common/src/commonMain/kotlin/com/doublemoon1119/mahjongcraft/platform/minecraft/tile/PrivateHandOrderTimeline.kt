package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 一張桌子上本人手牌排列的時間軸，與其他人看到的牌張動作依同一個遊戲時間生效。
 *
 * 項目可以比生效時間早到，也可以不依時間順序抵達；同一時間的項目以後到的為準。
 */
class PrivateHandOrderTimeline {
    /** 依生效時間排列的項目；已被更晚的生效項目取代的舊項目會在查詢時移除。 */
    private val entries = mutableListOf<PrivateHandOrderEntry>()

    /** 是否還沒收到任何項目，例如剛重新連線時。 */
    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * 加入一個項目。
     *
     * @param entry 要加入的項目。
     */
    fun add(entry: PrivateHandOrderEntry) {
        val index = entries.indexOfFirst { it.effectiveAt > entry.effectiveAt }.takeIf { it >= 0 } ?: entries.size
        entries.add(index, entry)
    }

    /**
     * 取得 [time] 當下生效的排列。
     *
     * @param time 本幀世界時間，包含部分 tick。
     * @return 生效時間不晚於 [time] 的最後一個項目的排列；還沒有項目生效時為 null。
     */
    fun orderAt(time: Double): PrivateHandOrder? {
        val current = entries.indexOfLast { it.effectiveAt <= time }
        if (current < 0) return null
        repeat(current) { entries.removeAt(0) }
        return entries.first().order
    }
}
