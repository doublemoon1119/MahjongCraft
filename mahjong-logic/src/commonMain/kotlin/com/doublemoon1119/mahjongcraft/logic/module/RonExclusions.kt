package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 對手依規則已經不能榮和的牌，牌面以規則的牌面正規化表示。
 *
 * @property ownDiscards 對手自己打過、因此不能榮和的牌。
 * @property passedAfterDeclaration 對手宣告後由其他玩家打出、對手沒有榮和，因此之後也不能榮和的牌。
 */
data class RonExclusions(
    val ownDiscards: Set<Tile> = emptySet(),
    val passedAfterDeclaration: Set<Tile> = emptySet(),
) {
    /** [RonExclusions] 的固定值。 */
    companion object {
        /** 沒有任何不能榮和的牌。 */
        val NONE: RonExclusions = RonExclusions()
    }
}
