package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState

/**
 * 簡易配對麻將本局固定的動態資料。
 *
 * @property bonusTileValues 本局公開展示並會在自摸時加分的牌面數值；同一局內不會重新抽選。
 */
data class ExamplePairingDynamicState(
    val bonusTileValues: Set<Int>,
) : DynamicRuleState {
    init {
        require(bonusTileValues.isNotEmpty()) { "Bonus tile values must not be empty" }
        require(bonusTileValues.all { it in 1..8 }) { "Bonus tile values must be between 1 and 8" }
    }
}
