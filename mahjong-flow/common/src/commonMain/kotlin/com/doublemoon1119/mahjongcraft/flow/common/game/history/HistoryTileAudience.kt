package com.doublemoon1119.mahjongcraft.flow.common.game.history

import kotlin.uuid.Uuid

/** 一筆交易後誰能知道某張牌的牌面。 */
sealed interface HistoryTileAudience {
    /** 本局所有合法觀察者都知道牌面。 */
    data object Public : HistoryTileAudience

    /**
     * 只有指定參與者知道牌面，例如持有者本人或規則授權的玩家。
     *
     * @property playerIds 知道牌面的參與者；空集合代表沒有任何參與者知道。
     */
    data class Players(val playerIds: Set<Uuid>) : HistoryTileAudience
}
