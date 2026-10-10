package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 一個共用時間邊界內同步開始的牌牆移動階段。
 *
 * @property motions 此階段內各張牌的路徑。
 */
data class TileWallMotionPhasePlan(
    val motions: List<TileWallPhaseMotion>,
) {
    init {
        require(motions.isNotEmpty()) { "Wall motion phase plan must contain at least one motion" }
    }

    /** 下一個 phase 最早可開始的相對 ticks。 */
    val durationTicks: Int = motions.maxOf { it.durationTicks }
}
