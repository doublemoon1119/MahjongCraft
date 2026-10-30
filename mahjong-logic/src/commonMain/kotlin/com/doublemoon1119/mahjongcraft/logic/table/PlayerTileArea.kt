package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 玩家持有但已移出手牌的獨立牌區。
 *
 * @property id 規則定義的完整區域識別碼。
 * @param tiles 依規則順序排列的當前牌張，建立時複製清單。
 */
class PlayerTileArea(val id: String, tiles: List<IdentifiedTile>) {
    /** 與來源清單分離的牌張版本。 */
    private val contents = tiles.toList()

    init {
        NamespacedId.requireValid(id) { "Player tile area ID must be namespaced: $id" }
        require(contents.map { it.id }.distinct().size == contents.size) { "Duplicate tile in player tile area" }
    }

    /** 目前牌張的獨立清單，保持區域內順序。 */
    val tiles: List<IdentifiedTile> get() = contents.toList()

    /** 依區域身分與有序牌張比較。 */
    override fun equals(other: Any?): Boolean = other is PlayerTileArea && id == other.id && contents == other.contents

    /** 與區域相等規則一致的雜湊值。 */
    override fun hashCode(): Int = 31 * id.hashCode() + contents.hashCode()

    /** 一般診斷只顯示區域身分及張數，不輸出牌面。 */
    override fun toString(): String = "PlayerTileArea(id=$id, count=${contents.size})"
}
