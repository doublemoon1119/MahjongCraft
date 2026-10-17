package com.doublemoon1119.mahjongcraft.flow.common.game.model

/**
 * 局前準備操作的設定。
 *
 * @property baseSeconds 每次玩家準備決策可使用的基本秒數。
 */
data class RoundPreparationConfig(
    val baseSeconds: Int,
) {
    init {
        require(baseSeconds > 0) { "Round preparation base time must be positive" }
    }
}
