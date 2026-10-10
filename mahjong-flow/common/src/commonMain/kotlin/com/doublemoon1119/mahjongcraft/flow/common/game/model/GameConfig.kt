package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * 建立一場遊戲所需的完整設定。
 *
 * @property ruleConfig 影響麻將合法動作與結算的規則設定。
 * @property flowConfig 影響伺服器流程、計時與觀看權限的設定。
 */
data class GameConfig(
    val ruleConfig: MahjongRuleConfig,
    val flowConfig: GameFlowConfig = GameFlowConfig(),
)
