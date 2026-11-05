package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import kotlin.uuid.Uuid

/**
 * 一張牌在一步之中的動作。
 *
 * @property tileId 牌的識別碼。
 * @property kind 動作種類。
 * @property fromRow 這一步開始時所在的那一排；[TileMotionKind.PLACE] 時為 null。
 * @property toRow 這一步結束時所在的那一排。
 * @property from 這一步開始時的位置；[TileMotionKind.PLACE] 時為 null。
 * @property to 這一步結束時的位置。
 * @property fromPose 這一步開始時的姿態；[TileMotionKind.PLACE] 時為 null。
 * @property toPose 這一步結束時的姿態。
 */
data class PlannedTileMotion(
    val tileId: Uuid,
    val kind: TileMotionKind,
    val fromRow: TileRowKey?,
    val toRow: TileRowKey,
    val from: MahjongTileWallPlacement?,
    val to: MahjongTileWallPlacement,
    val fromPose: TilePose?,
    val toPose: TilePose,
)
