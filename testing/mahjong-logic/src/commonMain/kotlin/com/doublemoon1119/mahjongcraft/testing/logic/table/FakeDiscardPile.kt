package com.doublemoon1119.mahjongcraft.testing.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile.DiscardEntry

/**
 * 用於單元測試的模擬牌河實作。
 *
 * 透過繼承 [DiscardEntry] 來定義測試用的捨牌紀錄實體，
 * 並實作 [DiscardPile] 介面以提供紀錄的存取與管理邏輯。
 *
 * 本類別為不可變值物件：[discard]、[takeLast] 皆不修改原實例，而是回傳新的 [FakeDiscardPile] 實例。
 */
data class FakeDiscardPile(
    private val _entries: List<FakeEntry> = emptyList()
) : DiscardPile<FakeDiscardPile.FakeEntry> {

    /** 獲取目前牌河中所有的紀錄列表。 */
    override val entries: List<FakeEntry> get() = _entries

    /**
     * 向模擬牌河添加一項捨牌紀錄。
     *
     * @param entry 測試用的捨牌紀錄實體。
     * @return 加入該紀錄後的新 [FakeDiscardPile] 實例。
     */
    override fun discard(entry: FakeEntry): FakeDiscardPile = copy(_entries = _entries + entry)

    /**
     * 建立一筆測試用的預設捨牌紀錄，並加入牌河。
     *
     * @param tile 欲捨棄的牌。
     * @return 加入該紀錄後的新 [FakeDiscardPile] 實例。
     */
    override fun discardTile(tile: IdentifiedTile): FakeDiscardPile = discard(FakeEntry(tile))

    /**
     * 標記最後一項紀錄已被取走。
     *
     * @return 標記後的新 [FakeDiscardPile] 實例；若牌河為空則回傳原實例。
     */
    override fun takeLast(): FakeDiscardPile {
        val last = _entries.lastOrNull() ?: return this
        return copy(_entries = _entries.dropLast(1) + last.withTaken())
    }

    /**
     * 測試用的捨牌紀錄實體類別。
     *
     * 透過建構子將 [tile] 與 [isTaken] 傳遞給父類別 [DiscardEntry]。
     */
    class FakeEntry(
        tile: IdentifiedTile,
        isTaken: Boolean = false
    ) : DiscardPile.DiscardEntry(tile, isTaken) {
        override fun withTaken(): FakeEntry = FakeEntry(tile, isTaken = true)
    }
}
