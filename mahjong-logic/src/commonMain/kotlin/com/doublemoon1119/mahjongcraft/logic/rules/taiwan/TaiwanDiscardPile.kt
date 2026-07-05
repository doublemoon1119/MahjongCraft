package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile.DiscardEntry

/**
 * 台灣麻將專用的牌河實作。
 *
 * 使用基礎的 [DiscardEntry] 作為紀錄型別，不包含立直等特殊狀態。
 */
class TaiwanDiscardPile : DiscardPile<DiscardPile.DiscardEntry> {
    private val _entries = mutableListOf<DiscardPile.DiscardEntry>()

    /**
     * 獲取目前牌河中所有的台灣麻將捨牌紀錄。
     */
    override val entries: List<DiscardPile.DiscardEntry> get() = _entries

    /**
     * 向牌河添加一項基礎捨牌紀錄。
     *
     * @param entry 基礎捨牌紀錄實體。
     */
    override fun discard(entry: DiscardPile.DiscardEntry) {
        _entries.add(entry)
    }

    /**
     * 標記最後一項紀錄已被取走。
     */
    override fun takeLast() {
        _entries.lastOrNull()?.isTaken = true
    }
}