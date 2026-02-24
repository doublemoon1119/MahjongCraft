package com.doublemoon1119.mahjongcraft.model.table

import com.doublemoon1119.mahjongcraft.model.base.IdentifiedTile

/**
 * 牌河介面。
 *
 * 負責管理並追蹤玩家打出至場上的廢牌紀錄。透過泛型 [T] 支援不同規則下的自定義紀錄格式。
 *
 * @param T 捨牌紀錄的型別，必須繼承自 [DiscardPile.DiscardEntry]。
 */
interface DiscardPile<T : DiscardPile.DiscardEntry> {
    /**
     * 獲取目前牌河中所有的紀錄。
     *
     * @return 包含所有 [T] 紀錄的唯讀列表。
     */
    val entries: List<T>

    /**
     * 向牌河添加一項捨牌紀錄。
     *
     * @param entry 具體的捨牌紀錄實體。
     */
    fun discard(entry: T)

    /**
     * 標記最後一項紀錄已被取走。
     *
     * 當其他玩家進行鳴牌動作（如吃、碰、槓）時呼叫，將最後一張捨牌的 [DiscardEntry.isTaken] 狀態設為 true。
     */
    fun takeLast()

    /**
     * 代表牌河中的單一紀錄實體基礎類別。
     *
     * @property tile 被打出的具有唯一標識的麻將牌。
     * @property isTaken 標記該張牌是否已被其他玩家鳴走。
     */
    open class DiscardEntry(
        val tile: IdentifiedTile,
        var isTaken: Boolean = false
    )
}