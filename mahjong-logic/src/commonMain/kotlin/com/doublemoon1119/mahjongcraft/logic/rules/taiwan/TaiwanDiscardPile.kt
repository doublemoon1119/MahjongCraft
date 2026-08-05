package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile.DiscardEntry

/**
 * 台灣麻將專用的牌河實作。
 *
 * 使用基礎的 [DiscardEntry] 作為紀錄型別，不包含立直等特殊狀態。
 *
 * 本類別為不可變值物件：[discard]、[takeLast] 皆不修改原實例，而是回傳新的 [TaiwanDiscardPile] 實例。
 */
data class TaiwanDiscardPile(
    private val _entries: List<DiscardPile.DiscardEntry> = emptyList()
) : DiscardPile<DiscardPile.DiscardEntry> {

    /**
     * 獲取目前牌河中所有的台灣麻將捨牌紀錄。
     */
    override val entries: List<DiscardPile.DiscardEntry> get() = _entries

    /**
     * 向牌河添加一項基礎捨牌紀錄。
     *
     * @param entry 基礎捨牌紀錄實體。
     * @return 加入該紀錄後的新 [TaiwanDiscardPile] 實例。
     */
    override fun discard(entry: DiscardPile.DiscardEntry): TaiwanDiscardPile = copy(_entries = _entries + entry)

    /**
     * 標記最後一項紀錄已被取走。
     *
     * @return 標記後的新 [TaiwanDiscardPile] 實例；若牌河為空則回傳原實例。
     */
    override fun takeLast(): TaiwanDiscardPile {
        val last = _entries.lastOrNull() ?: return this
        return copy(_entries = _entries.dropLast(1) + last.withTaken())
    }
}
