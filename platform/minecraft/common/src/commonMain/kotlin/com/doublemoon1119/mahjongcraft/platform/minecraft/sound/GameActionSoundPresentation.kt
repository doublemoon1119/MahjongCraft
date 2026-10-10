package com.doublemoon1119.mahjongcraft.platform.minecraft.sound

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/**
 * Minecraft 呈現層播放規則動作語音所需的宣告式資料。
 *
 * @property soundId 已由 Minecraft sound registry 註冊的完整 namespaced ID。
 * @property volume 播放音量。
 * @property pitch 播放音高。
 */
data class GameActionSoundPresentation(
    val soundId: String,
    val volume: Float = 1.0f,
    val pitch: Float = 1.0f,
) {
    init {
        MinecraftResourceIds.requireValid(soundId) { "Sound ID must be namespaced: $soundId" }
        require(volume >= 0.0f) { "Sound volume must not be negative" }
        require(pitch > 0.0f) { "Sound pitch must be positive" }
    }
}
