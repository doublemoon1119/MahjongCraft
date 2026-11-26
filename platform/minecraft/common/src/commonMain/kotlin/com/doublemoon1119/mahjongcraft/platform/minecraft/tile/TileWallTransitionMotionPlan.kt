package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 完整牌牆 transition 的確定性幾何與 phase barrier 計畫。
 *
 * @property phases 依權威順序排列的移動階段。
 */
data class TileWallTransitionMotionPlan(
    val phases: List<TileWallMotionPhasePlan>,
) {
    /** 全部階段播放完成所需的 ticks。 */
    val durationTicks: Int = phases.sumOf { it.durationTicks }
}
