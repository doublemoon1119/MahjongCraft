package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

/**
 * 單顆骰子接觸桌面時需要播放的聲音提示。
 *
 * @property tickOffset 相對骰子動畫開始時間的 server tick。
 * @property volume 此次接觸相對音量。
 * @property pitch 由動畫 seed 確定性產生的輕微音高差異。
 */
data class DiceLandingSoundCue(
    val tickOffset: Int,
    val volume: Float,
    val pitch: Float,
)
