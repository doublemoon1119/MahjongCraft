package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.uuid.Uuid

/**
 * 單張牌在一個規則 phase 中依序播放的幾何路徑。
 *
 * @property tileId 要移動的既有牌張 UUID。
 * @property segments 已按碰撞安全順序排列的路徑段。
 */
data class TileWallPhaseMotion(
    val tileId: Uuid,
    val segments: List<TileWallMotionSegment>,
) {
    init {
        require(segments.isNotEmpty()) { "Wall phase motion must contain at least one segment" }
    }

    /** 此牌完成本 phase 所需的 ticks。 */
    val durationTicks: Int = segments.sumOf { it.durationTicks }
}
