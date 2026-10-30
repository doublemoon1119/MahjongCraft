package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/**
 * 一張當前牌張所在的區域，不把被取走的舊捨牌列為第二個位置。
 *
 * @property tile 當前完整牌張，包含自己的組件。
 * @property region 目前所在區域。
 * @property playerAreaId 明確移出牌區的完整識別碼；其他區域為 null。
 * @property ownerId 區域所屬玩家；活牌與保留牌沒有持有人。
 */
data class TileAreaEntry(
    val tile: IdentifiedTile,
    val region: TileRegion,
    val ownerId: Uuid?,
    val playerAreaId: String? = null,
) {
    init {
        require((region == TileRegion.WALL || region == TileRegion.RESERVED) == (ownerId == null)) { "Tile area owner does not match its region" }
        require((region == TileRegion.SET_ASIDE) == (playerAreaId != null)) { "Set-aside tiles require an explicit player area" }
        playerAreaId?.let { NamespacedId.requireValid(it) { "Player tile area ID must be namespaced: $it" } }
    }
}
