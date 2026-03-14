package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.table.PlayerRuleState

/**
 * 日本麻將特有的玩家狀態。
 *
 * 用於記錄玩家在遊戲過程中的立直相關狀態。
 *
 * @property isRiichiDeclared 玩家是否已宣告立直。
 * @property isFuriten 玩家是否處於振聽狀態（即打過聽牌所需的牌，他家打同張不能榮和）。
 */
data class RiichiPlayerState(
    var isRiichiDeclared: Boolean = false,
    var isFuriten: Boolean = false
) : PlayerRuleState
