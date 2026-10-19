package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState

/**
 * 簡易配對麻將玩家在本局的規則狀態。
 *
 * @property declared 玩家是否已完成本局唯一一次示範宣告。
 */
data class ExamplePairingPlayerState(
    val declared: Boolean = false,
) : PlayerRuleState
