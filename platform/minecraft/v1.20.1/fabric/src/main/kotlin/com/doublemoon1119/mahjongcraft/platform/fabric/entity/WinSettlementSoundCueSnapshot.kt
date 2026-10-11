package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 和牌結算音效的同步快照。
 *
 * @property eventTick 音效相對演出開始的觸發 tick。
 * @property soundId 音效識別碼。
 * @property volume 音量。
 * @property pitch 音高。
 */
data class WinSettlementSoundCueSnapshot(
    val eventTick: Long,
    val soundId: String,
    val volume: Float,
    val pitch: Float,
)
