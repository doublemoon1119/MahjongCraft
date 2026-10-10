package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction

/**
 * 規則對一次自動操作情境的解析結果。
 *
 * @property immediateAction 可安全立即提交的動作；仍需玩家決定時為 null。
 * @property hiddenActions 已由控制明確拒絕、不應再提供給玩家的合法動作。
 */
data class AutomaticControlResult(
    val immediateAction: AutomaticControlAction? = null,
    val hiddenActions: Set<GameAction> = emptySet(),
) {
    init {
        require(immediateAction?.action !in hiddenActions) { "Immediate automatic action must not be hidden" }
    }
}
