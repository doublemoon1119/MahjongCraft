package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig

/**
 * 簡易配對麻將的初始分數設定。
 *
 * @property initialScore 每位玩家的起始分數。
 * @property bustThreshold 低於此分數時結束對局；本規則不使用此條件。
 */
data class ExamplePairingScoreConfig(
    override val initialScore: Int = 100,
    override val bustThreshold: Int? = null,
) : ScoreConfig
