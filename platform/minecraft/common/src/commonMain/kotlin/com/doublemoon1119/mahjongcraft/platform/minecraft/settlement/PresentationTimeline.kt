package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 以相對錨點描述的節點時間範圍。
 *
 * @property anchor 時間錨點。
 * @property offsetTicks 相對時間偏移。
 * @property durationTicks 持續時間。
 */
data class PresentationTimeline(
    val anchor: PresentationTimelineAnchor = PresentationTimelineAnchor.PANEL_START,
    val offsetTicks: Int = 0,
    val durationTicks: Int = 8,
) {
    init {
        require(offsetTicks in -1200..1200)
        require(durationTicks in 1..400)
    }
}
