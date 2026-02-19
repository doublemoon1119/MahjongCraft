package com.doublemoon1119.mahjongcraft.model

import java.util.*

/**
 * 管理具有唯一身份的手牌集合。
 *
 * 負責處理玩家手牌的整理、排序以及與外部標識符（UUID）的互動。
 *
 * @property tiles 已經整理在手中的牌列表。
 * @property lastDrawn 剛摸入但尚未放入手牌的牌。
 */
class Hand(
    private val tiles: MutableList<IdentifiedTile> = mutableListOf(),
    var lastDrawn: IdentifiedTile? = null
) {
    /**
     * 獲取所有手牌的唯讀列表（包含剛摸入的牌）。
     */
    val allTiles: List<IdentifiedTile>
        get() = tiles.toList() + listOfNotNull(lastDrawn)

    /**
     * 手牌總張數（包含剛摸入的牌）。
     */
    val size: Int get() = tiles.size + (if (lastDrawn != null) 1 else 0)

    /**
     * 處理摸牌動作。
     *
     * @param identifiedTile 帶有標識符的麻將牌。
     * @throws IllegalStateException 如果 [lastDrawn] 尚未被處理。
     */
    fun draw(identifiedTile: IdentifiedTile) {
        check(lastDrawn == null) { "Cannot draw tile: lastDrawn is not empty." }
        lastDrawn = identifiedTile
    }

    /**
     * 根據 UUID 從手牌中移除並打出特定的牌。
     *
     * 此方法支援「摸打」邏輯：若打出的是舊有的手牌，則會自動將 [lastDrawn] 填補入手牌列表中。
     *
     * @param id 欲打出的牌之唯一識別碼。
     * @return 被打出的 [IdentifiedTile]。
     * @throws IllegalArgumentException 若手牌中不存在該 UUID 的牌。
     */
    fun discardById(id: UUID): IdentifiedTile {
        // 優先檢查是否為剛摸入的牌
        if (lastDrawn?.id == id) {
            val t = lastDrawn!!
            lastDrawn = null
            return t
        }

        val found = tiles.find { it.id == id }
            ?: throw IllegalArgumentException("Hand does not contain tile with ID: $id")

        tiles.remove(found)

        // 補位邏輯：若打出舊牌，將最新的摸牌併入主手牌
        lastDrawn?.let {
            tiles.add(it)
            lastDrawn = null
        }
        return found
    }

    /**
     * 整理並排序手牌。
     *
     * [lastDrawn] 將會被合併入主手牌列表中，隨後根據指定的策略進行排序。
     *
     * @param order 排序策略（如 RiichiTileOrder 或 TaiwanTileOrder）。
     */
    fun organize(order: TileOrder) {
        lastDrawn?.let {
            tiles.add(it)
            lastDrawn = null
        }
        tiles.sortWith { a, b -> order.compare(a.tile, b.tile) }
    }
}