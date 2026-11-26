package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 一位玩家在一輪換牌中公開移出的牌。
 *
 * @property playerId 移出牌的玩家。
 * @property tileIds 移出的牌，依移出順序排列。
 */
data class TileReplacementRemoval(
    val playerId: Uuid,
    val tileIds: List<Uuid>,
) {
    init {
        require(tileIds.isNotEmpty()) { "A replacement removal must contain at least one tile" }
    }
}
