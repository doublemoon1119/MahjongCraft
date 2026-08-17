package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.SidewaysMarkedDiscardPile
import kotlin.uuid.Uuid

/**
 * 日本麻將專用的牌河紀錄實體。
 *
 * 擴充基礎紀錄以支援日本麻將特有的立直狀態。
 *
 * @param tile 被打出的具有唯一標識的麻將牌。
 * @param isRiichi 標記該張牌是否為宣告立直時所打出的牌。
 * @param isTaken 標記該張牌是否已被其他玩家鳴走。
 */
class RiichiDiscardEntry(
    tile: IdentifiedTile,
    val isRiichi: Boolean = false,
    isTaken: Boolean = false,
) : DiscardPile.DiscardEntry(tile, isTaken) {
    override fun withTaken(): RiichiDiscardEntry = RiichiDiscardEntry(tile, isRiichi, isTaken = true)
}

/**
 * 日本麻將專用的牌河實作。
 *
 * 明確指定紀錄型別為 [RiichiDiscardEntry]，以便精確追蹤立直與振聽相關狀態。
 *
 * 本類別為不可變值物件：[discard]、[takeLast] 皆不修改原實例，而是回傳新的 [RiichiDiscardPile] 實例。
 */
data class RiichiDiscardPile(
    private val _entries: List<RiichiDiscardEntry> = emptyList(),
) : DiscardPile<RiichiDiscardEntry>,
    SidewaysMarkedDiscardPile {

    /**
     * 獲取目前牌河中所有的日本麻將捨牌紀錄。
     */
    override val entries: List<RiichiDiscardEntry> get() = _entries

    /**
     * 從宣告立直的那張牌開始往後找第一張還沒被鳴走的牌——立直宣告牌本身沒被鳴走時就是它自己；
     * 被鳴走後（`takeLast()` 不會真的移除該筆紀錄，只標記 [RiichiDiscardEntry.isTaken]，
     * [RiichiDiscardEntry.isRiichi] 透過 [RiichiDiscardEntry.withTaken] 保留），標記自然往後移到
     * 下一張還留著的牌，連續好幾張都被鳴走也一樣正確；還沒有下一張時回傳 `null`，不需要額外的
     * 位移狀態機，下一次捨牌時再次呼叫本方法就會自然補上。
     */
    override fun sidewaysMarkedTileId(): Uuid? {
        val declaredIndex = _entries.indexOfFirst { it.isRiichi }
        if (declaredIndex == -1) return null
        return _entries.drop(declaredIndex).firstOrNull { !it.isTaken }?.tile?.id
    }

    /**
     * 向牌河添加一項日本麻將捨牌紀錄。
     *
     * @param entry 日本麻將專用的捨牌紀錄實體。
     * @return 加入該紀錄後的新 [RiichiDiscardPile] 實例。
     */
    override fun discard(entry: RiichiDiscardEntry): RiichiDiscardPile = copy(_entries = _entries + entry)

    /**
     * 建立一筆非立直宣告的預設捨牌紀錄，並加入牌河。
     *
     * @param tile 欲捨棄的牌。
     * @return 加入該紀錄後的新 [RiichiDiscardPile] 實例。
     */
    override fun discardTile(tile: IdentifiedTile): RiichiDiscardPile = discard(RiichiDiscardEntry(tile))

    /**
     * 標記最後一項紀錄已被取走。
     *
     * @return 標記後的新 [RiichiDiscardPile] 實例；若牌河為空則回傳原實例。
     */
    override fun takeLast(): RiichiDiscardPile {
        val last = _entries.lastOrNull() ?: return this
        return copy(_entries = _entries.dropLast(1) + last.withTaken())
    }
}
