package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import kotlin.uuid.Uuid

/**
 * 一張牌在某份桌況中擺放的位置與姿態。
 *
 * @property tileId 牌的識別碼。
 * @property row 牌所在的那一排。
 * @property orderInRow 牌在那一排中的順序。
 * @property placement 世界座標與朝向。
 * @property pose 姿態。
 */
data class ArrangedTile(
    val tileId: Uuid,
    val row: TileRowKey,
    val orderInRow: Int,
    val placement: MahjongTileWallPlacement,
    val pose: TilePose,
)
