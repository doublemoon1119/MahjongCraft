package com.doublemoon1119.mahjongcraft.logic.base

import kotlin.uuid.Uuid

/**
 * [IdentifiedTile] 對指定觀察者的可見快照。
 *
 * @property id 實體牌的唯一識別碼，即使牌面隱藏仍保留。
 * @property tile 可見時的牌面；隱藏時為 null。
 */
data class IdentifiedTileSnapshot(
    val id: Uuid,
    val tile: Tile?,
)

/**
 * 產生一個 [IdentifiedTile] 的快照。
 *
 * @param isVisible 是否公開這張牌的牌面。
 * @return 保留實體牌 ID，並依可見性處理牌面的快照。
 */
fun IdentifiedTile.toSnapshot(isVisible: Boolean): IdentifiedTileSnapshot = IdentifiedTileSnapshot(
    id = this.id,
    tile = this.tile.takeIf { isVisible },
)
