package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 可持久化的 showcase 額外音效快照。
 *
 * @property soundId 音效識別碼。
 * @property tickOffset 相對演出開始的觸發 tick。
 * @property volume 音量。
 * @property pitch 音高。
 */
data class ShowcaseSoundSnapshot(val soundId: String, val tickOffset: Int, val volume: Float, val pitch: Float)
