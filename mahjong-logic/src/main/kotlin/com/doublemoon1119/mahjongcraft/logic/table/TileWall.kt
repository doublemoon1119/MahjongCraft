package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile

/**
 * 代表一場遊戲中的牌山。
 *
 * 內部存儲 [IdentifiedTile]，確保每一張牌在遊戲進程中都具有可追蹤的唯一性。
 */
class TileWall(tiles: List<IdentifiedTile>) {
    /**
     * 牌山的牌列表。
     */
    private val tiles: MutableList<IdentifiedTile> = tiles.toMutableList()

    /**
     * 剩餘牌數。
     */
    val remainingCount: Int get() = tiles.size

    /**
     * 從牌山最前方摸取一張牌。
     *
     * @return 摸到的 [IdentifiedTile]，若牌山已空則返回 null。
     */
    fun draw(): IdentifiedTile? = tiles.removeFirstOrNull()

    /**
     * 從牌山最後方摸取一張牌 (嶺上)。
     *
     * @return 摸到的 [IdentifiedTile]，若牌山已空則返回 null。
     */
    fun drawLast(): IdentifiedTile? = tiles.removeLastOrNull()

    /**
     * 僅查看特定位置的牌，不移除。
     */
    fun peekAt(index: Int): IdentifiedTile? = tiles.getOrNull(index)

    /**
     * 獲取目前牌山的唯讀列表。
     */
    fun getAllTiles(): List<IdentifiedTile> = tiles.toList()
}