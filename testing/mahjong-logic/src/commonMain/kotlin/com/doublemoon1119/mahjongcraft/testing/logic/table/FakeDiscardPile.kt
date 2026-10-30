package com.doublemoon1119.mahjongcraft.testing.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile.DiscardEntry

/**
 * 用於單元測試的模擬牌河實作。
 *
 * 透過繼承 [DiscardEntry] 來定義測試用的捨牌紀錄實體，
 * 並實作 [DiscardPile] 介面以提供紀錄的存取與管理邏輯。
 *
 * [discard]、[takeLast] 都會回傳新實例，不會直接修改原牌河紀錄。
 *
 * @property _entries 建立時的牌河紀錄，預設為空列表。
 */
data class FakeDiscardPile(
    private val _entries: List<FakeEntry> = emptyList(),
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

    /** 替換當前牌張，已取走的舊紀錄保持原值。 */
    override fun replaceTile(tile: IdentifiedTile): FakeDiscardPile {
        val entries = _entries.map { if (!it.isTaken && it.tile.id == tile.id) it.withTile(tile) else it }
        require(entries.count { !it.isTaken && it.tile.id == tile.id } == 1) { "Tile is not in the current discard area" }
        return copy(_entries = entries)
    }

    /** 替換指定紀錄的組件，其他紀錄保持原值。 */
    override fun replaceEntryComponents(index: Int, components: ComponentSet): FakeDiscardPile {
        require(index in _entries.indices) { "Discard entry index is out of range" }
        return copy(_entries = _entries.mapIndexed { position, entry -> if (position == index) entry.withComponents(components) else entry })
    }

    /**
     * 測試用的捨牌紀錄實體類別。
     *
     * 透過建構子將 [tile] 與 [isTaken] 傳遞給父類別 [DiscardEntry]。
     *
     * @param tile 這筆捨牌紀錄所指的牌及其 UUID。
     * @param isTaken 這張牌是否已被吃、碰或槓走，預設為 false。
     */
    class FakeEntry(
        tile: IdentifiedTile,
        isTaken: Boolean = false,
        components: ComponentSet = ComponentSet.EMPTY,
    ) : DiscardPile.DiscardEntry(tile, isTaken, components) {
        override fun withTaken(): FakeEntry = FakeEntry(tile, isTaken = true, components = components)

        override fun withTile(replacement: IdentifiedTile): FakeEntry {
            require(replacement.id == tile.id && replacement.tile == tile.tile) { "Replacement must preserve tile identity and face" }
            return FakeEntry(replacement, isTaken, components)
        }

        override fun withComponents(replacement: ComponentSet): FakeEntry = FakeEntry(tile, isTaken, replacement)
    }
}
