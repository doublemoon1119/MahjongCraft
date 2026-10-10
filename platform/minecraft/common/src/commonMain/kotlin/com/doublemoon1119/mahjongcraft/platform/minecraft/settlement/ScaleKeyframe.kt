package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 單一縮放關鍵影格。
 *
 * @property progress 動畫進度。
 * @property scale 縮放值。
 */
data class ScaleKeyframe(
    val progress: Float,
    val scale: Float,
) {
    init {
        require(progress in 0f..1f && scale in 0f..4f)
    }
}
