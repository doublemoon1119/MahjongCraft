package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
/**
 * [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishDiceRoll] 隨附桌況資料。
 *
 * @property dealerSeatIndex 莊家座位索引。
 * @property roundNumber 局數。
 * @property comboCount 連莊或本場數。
 */
data class DiceRollContext(
    val dealerSeatIndex: Int,
    val roundNumber: Int,
    val comboCount: Int,
)
