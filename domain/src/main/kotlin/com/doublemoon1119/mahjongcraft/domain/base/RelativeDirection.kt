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

    /**
     * 來源於玩家自身。
     *
     * 用於表示動作的觸發點是玩家自己，例如自摸 (Tsumo) 或暗槓 (Closed Kan)。
     */
    data object Self : RelativeDirection()
}
