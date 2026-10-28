package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState

/**
 * 簡易配對麻將玩家在本局的規則狀態。
 *
 * @property handRevealed 玩家是否已完成本局唯一一次攤牌。
 */
data class ExamplePairingPlayerState(
    val handRevealed: Boolean = false,
) : PlayerRuleState
