package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.time.Duration

/**
 * 規則選擇操作的設定。
 *
 * @property base 每次玩家規則選擇可使用的基本時間，必須是正的整數秒。
 */
data class RuleDecisionConfig(
    val base: Duration,
) {
    init {
        require(base.isPositive() && base.isNonNegativeWholeSeconds()) {
            "Rule decision base time must be a positive whole number of seconds"
        }
    }
}
