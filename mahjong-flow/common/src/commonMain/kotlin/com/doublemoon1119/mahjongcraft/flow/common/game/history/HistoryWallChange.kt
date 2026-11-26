package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.TileWall

/**
 * 活牌牆兩端移除與留下牌張的組件變化。
 *
 * @property updatedTiles 留下牌張的新組件版本，不改變其身分、基本牌面或順序。
 * @property removedFront 從摸牌端移除的張數。
 * @property removedBack 從另一端移除的張數。
 */
data class HistoryWallChange(
    val removedFront: Int,
    val removedBack: Int,
    val updatedTiles: List<IdentifiedTile> = emptyList(),
) {
    init {
        require(removedFront >= 0 && removedBack >= 0)
        require(updatedTiles.map { it.id }.distinct().size == updatedTiles.size) { "Duplicate updated wall tile" }
    }

    /**
     * 從前一個牌牆重建留下的牌序與組件版本。
     *
     * @param before 交易前的活牌牆。
     * @return 移除牌張並套用組件變化後的活牌牆。
     * @throws IllegalArgumentException 移除張數總和超過原牌牆張數時拋出。
     */
    fun applyTo(before: TileWall): TileWall {
        val tiles = before.getAllTiles()
        require(removedFront + removedBack <= tiles.size) { "History wall removal exceeds source wall" }
        val retained = tiles.subList(removedFront, tiles.size - removedBack)
        val originals = retained.associateBy { it.id }
        require(updatedTiles.all { originals[it.id]?.tile == it.tile }) {
            "Updated wall tile must retain an existing identity and face"
        }
        val replacements = updatedTiles.associateBy { it.id }
        return TileWall(retained.map { replacements[it.id] ?: it })
    }
}
