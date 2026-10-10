package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.logic.table.TileWall

/**
 * 活牌牆僅從兩端移除牌張的變化。
 *
 * @property removedFront 從摸牌端移除的張數。
 * @property removedBack 從另一端移除的張數。
 */
data class HistoryWallChange(
    val removedFront: Int,
    val removedBack: Int,
) {
    init {
        require(removedFront >= 0 && removedBack >= 0)
    }

    /** 從前一個牌牆重建留下的牌序。 */
    fun applyTo(before: TileWall): TileWall {
        val tiles = before.getAllTiles()
        require(removedFront + removedBack <= tiles.size) { "History wall removal exceeds source wall" }
        return TileWall(tiles.subList(removedFront, tiles.size - removedBack))
    }
}
