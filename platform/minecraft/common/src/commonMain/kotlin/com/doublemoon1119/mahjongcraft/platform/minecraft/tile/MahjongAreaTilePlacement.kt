package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.uuid.Uuid

/**
 * 牌區中一張牌的最終位置。
 *
 * @property tileId 牌的識別碼。
 * @property placement 世界座標與朝向。
 * @property footprint 這張牌在桌面上佔用的形狀。
 * @property alongOffsetFromCorner 副露與移出的牌中心沿排列方向距離桌角錨點的距離；立牌為 null。
 * @property isFaceDown 是否蓋著擺放。
 */
data class MahjongAreaTilePlacement(
    val tileId: Uuid,
    val placement: MahjongTileWallPlacement,
    val footprint: MahjongTileFootprint,
    val alongOffsetFromCorner: Double?,
    val isFaceDown: Boolean = false,
)
