package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
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
}
