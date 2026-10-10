package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

/**
 * showcase 階段可額外播放的宣告式音效。
 *
 * @property soundId 音效 ID。
 * @property tickOffset 音效相對展示階段開始的 tick 偏移。
 * @property volume 音量。
 * @property pitch 音調。
 */
data class ShowcaseSound(
    val soundId: String,
    val tickOffset: Int,
    val volume: Float = 1.0f,
    val pitch: Float = 1.0f,
)
