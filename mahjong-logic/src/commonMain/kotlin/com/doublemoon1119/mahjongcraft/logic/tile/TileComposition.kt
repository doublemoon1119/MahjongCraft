package com.doublemoon1119.mahjongcraft.logic.tile

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * 同時供建牌與假設計算使用的不可變牌組組成。
 *
 * @param entries 各種牌值及其張數；重複牌值會被拒絕。
 */
class TileComposition(entries: List<TileCompositionEntry>) {
    /** 與來源獨立、保持規則順序的牌值清單。 */
    private val values = entries.toList()

    init {
        require(values.isNotEmpty()) { "Tile composition must not be empty" }
        require(values.map { it.value }.distinct().size == values.size) { "Duplicate tile composition value" }
        val total = values.sumOf { it.count.toLong() }
        require(total <= Int.MAX_VALUE) { "Tile composition size exceeds supported range" }
    }

    /** 此牌組的總張數，不建立牌張。 */
    val tileCount: Int get() = values.sumOf { it.count }

    /**
     * 取得獨立的組成清單，不產生 UUID、不洗牌。
     *
     * @return 依規則順序排列的牌值與張數。
     */
    fun entries(): List<TileCompositionEntry> = values.toList()

    /**
     * 查詢一種完整牌值的張數，不合併不同特性。
     *
     * @param value 要查詢的牌值。
     * @return 張數；沒有時為 0。
     */
    fun count(value: TileValue): Int = values.firstOrNull { it.value == value }?.count ?: 0

    /**
     * 建立帶有原特性的實際牌張，再洗牌；純組成查詢不呼叫此方法。
     *
     * @param random 產生牌張 ID 與洗牌使用的亂數來源；同一個來源狀態產生同一副牌山。
     * @return 新建立的牌山。
     */
    fun createWall(random: Random): TileWall {
        val tiles = values.flatMap { entry ->
            List(entry.count) { IdentifiedTile(Uuid.fromLongs(random.nextLong(), random.nextLong()), entry.value.tile, entry.value.components) }
        }
        return TileWall(tiles.shuffled(random))
    }
}
