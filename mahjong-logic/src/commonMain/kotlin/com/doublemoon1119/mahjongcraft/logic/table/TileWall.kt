package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile

/**
 * 代表一場遊戲中的牌山。
 *
 * 內部存儲 [IdentifiedTile]，確保每一張牌在遊戲進程中都具有可追蹤的唯一性。
 *
 * 本類別為不可變值物件：[draw]、[drawLast] 皆不會修改原實例，
 * 而是透過 [DrawResult] 回傳摸到的牌與反映變更後狀態的新 [TileWall] 實例。
 */
data class TileWall(private val tiles: List<IdentifiedTile> = emptyList()) {

    /**
     * 摸牌動作的結果封裝。
     *
     * @property tile 摸到的 [IdentifiedTile]，若牌山已空則為 null。
     * @property wall 摸牌後的新 [TileWall] 實例。
     */
    data class DrawResult(val tile: IdentifiedTile?, val wall: TileWall)

    /**
     * 剩餘牌數。
     */
    val remainingCount: Int get() = tiles.size

    /**
     * 從牌山最前方摸取一張牌。
     *
     * @return 包含摸到的牌與新牌山狀態的 [DrawResult]；若牌山已空，[DrawResult.tile] 為 null 且牌山維持不變。
     */
    fun draw(): DrawResult {
        val tile = tiles.firstOrNull() ?: return DrawResult(null, this)
        return DrawResult(tile, TileWall(tiles.subList(1, tiles.size)))
    }

    /**
     * 從牌山最後方摸取一張牌 (嶺上)。
     *
     * @return 包含摸到的牌與新牌山狀態的 [DrawResult]；若牌山已空，[DrawResult.tile] 為 null 且牌山維持不變。
     */
    fun drawLast(): DrawResult {
        val tile = tiles.lastOrNull() ?: return DrawResult(null, this)
        return DrawResult(tile, TileWall(tiles.subList(0, tiles.size - 1)))
    }

    /**
     * 僅查看特定位置的牌，不移除。
     */
    fun peekAt(index: Int): IdentifiedTile? = tiles.getOrNull(index)

    /**
     * 獲取目前牌山的唯讀列表。
     */
    fun getAllTiles(): List<IdentifiedTile> = tiles
}
