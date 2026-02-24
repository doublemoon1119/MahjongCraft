package com.doublemoon1119.mahjongcraft.model.table

import com.doublemoon1119.mahjongcraft.model.base.IdentifiedTile

/**
 * 代表一場遊戲中的牌山。
 *
 * 內部存儲 [IdentifiedTile]，確保每一張牌在遊戲進程中都具有可追蹤的唯一性。
 *
 * @property tiles 牌山中剩餘的牌列表。
 */
class TileWall(private val tiles: MutableList<IdentifiedTile>) {

    /**
     * 剩餘牌數。
     */
    val remainingCount: Int get() = tiles.size

    /**
     * 進行洗牌。
     */
    fun shuffle() {
        tiles.shuffle()
    }

    /**
     * 從牌山中摸取一張牌。
     *
     * @return 摸到的 [IdentifiedTile]，若牌山已空則返回 null。
     */
    fun draw(): IdentifiedTile? {
        if (tiles.isEmpty()) return null
        return tiles.removeAt(0)
    }

    /**
     * 獲取目前牌山的唯讀列表（用於調試或狀態同步）。
     */
    fun getAllTiles(): List<IdentifiedTile> = tiles.toList()
}