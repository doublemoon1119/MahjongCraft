package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/**
 * 在指定錨點播放的宣告式聲音。
 *
 * @property soundId 聲音 resource ID。
 * @property anchor 播放時間錨點。
 * @property offsetTicks 相對錨點偏移。
 * @property volume 音量。
 * @property pitch 音高。
 */
data class PresentationSoundCue(
    val soundId: String,
    val anchor: PresentationTimelineAnchor,
    val offsetTicks: Int = 0,
    val volume: Float = 0.35f,
    val pitch: Float = 1f,
) {
    init {
        MinecraftResourceIds.requireValid(soundId) { "Invalid presentation sound ID: $soundId" }
        require(offsetTicks in -1200..1200 && volume in 0f..4f && pitch in 0.1f..4f)
    }
}
