package com.doublemoon1119.mahjongcraft.logic.base

import kotlin.uuid.Uuid

/**
 * 一張牌對指定讀取者的可見資料。
 *
 * @property id 局內牌張的穩定識別碼；未知牌仍保留。
 * @property face 已授權的牌面與顯示特徵；未知牌為 null，不攜帶其私有特徵。
 */
data class IdentifiedTileSnapshot(
    val id: Uuid,
    val face: TileFaceView?,
) {
    /** 目前可讀取的基本牌面；未知牌為 null。 */
    val tile: Tile? get() = face?.tile
}

/**
 * 建立只含基本牌面的快照；規則特徵由正式可見性投影加入。
 *
 * @param isVisible 讀取者是否知道這張牌的牌面。
 * @return 保留牌張身分並裁切牌面的快照。
 */
fun IdentifiedTile.toSnapshot(isVisible: Boolean): IdentifiedTileSnapshot = IdentifiedTileSnapshot(
    id = id,
    face = tile.takeIf { isVisible }?.let(::TileFaceView),
)
