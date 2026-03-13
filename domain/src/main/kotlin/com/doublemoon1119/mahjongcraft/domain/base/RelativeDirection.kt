package com.doublemoon1119.mahjongcraft.domain.base

/**
 * 定義相對於玩家的方位。
 *
 * 用於描述鳴牌的來源方向，或玩家的座位。
 */
sealed class RelativeDirection {
    /** 上家 (Left) */
    data object Left : RelativeDirection()

    /** 對家 (Across) */
    data object Across : RelativeDirection()

    /** 下家 (Right) */
    data object Right : RelativeDirection()

    /** 無 (None) */
    data object None : RelativeDirection()
}
