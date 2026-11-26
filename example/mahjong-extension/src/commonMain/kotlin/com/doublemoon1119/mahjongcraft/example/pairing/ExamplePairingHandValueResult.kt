package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult

/**
 * 簡易配對麻將的胡牌結果。
 *
 * @property bonusTileCount 胡牌手牌命中的不同加分牌種類數量。
 */
data class ExamplePairingHandValueResult(
    val bonusTileCount: Int = 0,
) : HandValueResult
