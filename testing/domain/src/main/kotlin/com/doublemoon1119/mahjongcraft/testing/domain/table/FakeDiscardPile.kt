package com.doublemoon1119.mahjongcraft.testing.domain.table

import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.table.DiscardPile

/**
 * 用於單元測試的模擬牌河實作。
 *
 * 透過繼承 [DiscardEntry] 來定義測試用的捨牌紀錄實體，
 * 並實作 [DiscardPile] 介面以提供紀錄的存取與管理邏輯。
 */
class FakeDiscardPile : DiscardPile<FakeDiscardPile.FakeEntry> {

    /** 儲存模擬捨牌紀錄的可變列表。 */
    private val _entries = mutableListOf<FakeEntry>()

    /** 獲取目前牌河中所有的紀錄列表。 */
    override val entries: List<FakeEntry> get() = _entries

    /**
     * 向模擬牌河添加一項捨牌紀錄。
     *
     * @param entry 測試用的捨牌紀錄實體。
     */
    override fun discard(entry: FakeEntry) {
        _entries.add(entry)
    }

    /**
     * 標記最後一項紀錄已被取走。
     */
    override fun takeLast() {
        _entries.lastOrNull()?.let {
            it.isTaken = true
        }
    }

    /**
     * 測試用的捨牌紀錄實體類別。
     *
     * 透過建構子將 [tile] 與 [isTaken] 傳遞給父類別 [DiscardEntry]。
     */
    class FakeEntry(
        tile: IdentifiedTile,
        isTaken: Boolean = false
    ) : DiscardPile.DiscardEntry(tile, isTaken)
}