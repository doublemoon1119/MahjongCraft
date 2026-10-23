package com.doublemoon1119.mahjongcraft.flow.common.game.model

/**
 * 規則選擇操作的設定。
 *
 * @property baseSeconds 每次玩家規則選擇可使用的基本秒數。
 */
data class RuleDecisionConfig(
    val baseSeconds: Int,
) {
    init {
        require(baseSeconds > 0) { "Rule decision base time must be positive" }
    }
}
