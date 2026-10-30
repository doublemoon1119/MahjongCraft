package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import kotlin.uuid.Uuid

/**
 * 玩家各個移出牌區的不可變集合，同一張當前牌不能同時放在兩個區域。
 *
 * @param areas 依規則順序排列的獨立牌區。
 */
class PlayerTileAreas(areas: List<PlayerTileArea>) {
    /** 與來源集合獨立的區域清單。 */
    private val contents = areas.toList()

    init {
        require(contents.map { it.id }.distinct().size == contents.size) { "Duplicate player tile area ID" }
        val tileIds = contents.flatMap { area -> area.tiles.map { it.id } }
        require(tileIds.distinct().size == tileIds.size) { "A player tile occupies multiple areas" }
    }

    /** 所有區域的獨立清單，保留規則順序。 */
    val areas: List<PlayerTileArea> get() = contents.toList()

    /** 各區域目前的牌張，依區域及區域內順序排列。 */
    val allTiles: List<IdentifiedTile> get() = contents.flatMap { it.tiles }

    /**
     * 取得指定區域，不以空集合掩蓋未建立的區域。
     *
     * @param id 區域完整識別碼。
     * @return 對應區域；不存在時為 null。
     */
    operator fun get(id: String): PlayerTileArea? = contents.firstOrNull { it.id == id }

    /**
     * 更新或新增一個區域，不影響其他區域的牌或順序。
     *
     * @param id 區域完整識別碼。
     * @param tiles 此區域的新牌張順序。
     * @return 更新後的區域集合。
     */
    fun withArea(id: String, tiles: List<IdentifiedTile>): PlayerTileAreas {
        val replacement = PlayerTileArea(id, tiles)
        val updated = if (contents.any { it.id == id }) contents.map { if (it.id == id) replacement else it } else contents + replacement
        return PlayerTileAreas(updated)
    }

    /**
     * 將一張當前牌替換為相同身分的新版本，不搬動其區域或位置。
     *
     * @param tileId 要更新的牌張身分。
     * @param replacement 保持身分與基本牌面的新版本。
     * @return 更新後的區域集合。
     */
    fun replaceTile(tileId: Uuid, replacement: IdentifiedTile): PlayerTileAreas {
        val original = requireNotNull(allTiles.singleOrNull { it.id == tileId }) { "Tile is not held in a player area" }
        require(replacement.id == original.id && replacement.tile == original.tile) { "Area tile replacement must preserve identity and face" }
        return PlayerTileAreas(
            contents.map { area ->
                PlayerTileArea(area.id, area.tiles.map { if (it.id == tileId) replacement else it })
            },
        )
    }

    /** 依有序區域與牌張比較。 */
    override fun equals(other: Any?): Boolean = other is PlayerTileAreas && contents == other.contents

    /** 與區域集合相等規則一致的雜湊值。 */
    override fun hashCode(): Int = contents.hashCode()

    /** 一般診斷不輸出牌面或組件值。 */
    override fun toString(): String = "PlayerTileAreas(${contents.joinToString { it.id }})"

    companion object {
        /** 尚無移出牌區的集合。 */
        val EMPTY = PlayerTileAreas(emptyList())
    }
}
