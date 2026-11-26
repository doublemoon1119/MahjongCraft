package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/** 流局公開手牌與等待牌的持久化表示。 */
@Serializable
data class RevealedHandSettlementPersistenceDto(
    /** 公開手牌所屬玩家的 UUID 字串。 */
    val playerId: String,
    /** 該玩家流局時的等待牌。 */
    val waitingTiles: List<TilePersistenceDto>,
)
