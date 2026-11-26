package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 一段由絕對起點移至絕對終點的確定性線性運動。
 *
 * @property start 本段畫面起點。
 * @property end 本段真實終點。
 * @property durationTicks 本段播放時間。
 */
data class TileWallMotionSegment(
    val start: MahjongTileWallPlacement,
    val end: MahjongTileWallPlacement,
    val durationTicks: Int,
) {
    init {
        require(durationTicks > 0) { "Wall motion segment duration must be positive" }
    }
}
