package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.server.game.model.PlayerDecisionOptions
import com.doublemoon1119.mahjongcraft.logic.module.AutomaticControlAction

/**
 * 一次自動操作政策評估後的玩家可見選項與立即動作。
 *
 * @property options 套用自動操作政策後仍可供玩家使用的決策選項。
 * @property immediateAction 政策要求立即執行的動作；沒有立即動作時為 null。
 */
data class EvaluatedPlayerDecision(
    val options: PlayerDecisionOptions,
    val immediateAction: AutomaticControlAction?,
)
