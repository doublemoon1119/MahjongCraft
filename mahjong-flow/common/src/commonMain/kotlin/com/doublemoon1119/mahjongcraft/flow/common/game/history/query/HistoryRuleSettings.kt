package com.doublemoon1119.mahjongcraft.flow.common.game.history.query
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig

/**
 * 歷史對局開局時採用的完整遊戲設定。
 *
 * @property config 開局時採用的遊戲規則與流程設定。
 */
data class HistoryRuleSettings(
    val config: GameConfig,
)
