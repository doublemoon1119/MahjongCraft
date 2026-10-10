package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import kotlinx.serialization.Serializable

/**
 * [PendingReaction] 的完整 persistence DTO。
 *
 * @property discarderId 捨牌者的玩家 UUID 字串。
 * @property tileId 被捨出的牌張 UUID 字串。
 * @property eligiblePlayerIds 可提交反應的玩家 UUID 字串集合。
 * @property responses 各玩家已提交的反應動作。
 */
@Serializable
data class PendingReactionPersistenceDto(
    val discarderId: String,
    val tileId: String,
    val eligiblePlayerIds: Set<String>,
    val responses: Map<String, GameActionPersistenceDto>,
)
