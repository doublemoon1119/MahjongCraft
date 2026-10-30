package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile

/**
 * 簡易配對麻將使用的基本牌河。
 *
 * @property entriesInternal 依捨牌順序排列的牌張與取走標記。
 */
data class ExamplePairingDiscardPile(
    private val entriesInternal: List<DiscardPile.DiscardEntry> = emptyList(),
) : DiscardPile<DiscardPile.DiscardEntry> {
    override val entries: List<DiscardPile.DiscardEntry> get() = entriesInternal

    override fun discard(entry: DiscardPile.DiscardEntry): ExamplePairingDiscardPile = copy(entriesInternal = entriesInternal + entry)

    override fun discardTile(tile: IdentifiedTile): ExamplePairingDiscardPile = discard(DiscardPile.DiscardEntry(tile))

    override fun takeLast(): ExamplePairingDiscardPile {
        val last = entriesInternal.lastOrNull() ?: return this
        return copy(entriesInternal = entriesInternal.dropLast(1) + last.withTaken())
    }

    /** 替換目前仍在牌河的同張牌，不修改被取走的歷史紀錄。 */
    override fun replaceTile(tile: IdentifiedTile): ExamplePairingDiscardPile {
        val entries = entriesInternal.map { entry ->
            if (!entry.isTaken && entry.tile.id == tile.id) entry.withTile(tile) else entry
        }
        require(entries.count { !it.isTaken && it.tile.id == tile.id } == 1) { "Tile is not in the current discard area" }
        return copy(entriesInternal = entries)
    }

    /** 替換指定紀錄的組件，其他紀錄保持原值。 */
    override fun replaceEntryComponents(index: Int, components: ComponentSet): ExamplePairingDiscardPile {
        require(index in entriesInternal.indices) { "Discard entry index is out of range" }
        return copy(entriesInternal = entriesInternal.mapIndexed { position, entry -> if (position == index) entry.withComponents(components) else entry })
    }
}
