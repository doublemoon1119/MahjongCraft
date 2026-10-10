package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import kotlinx.serialization.Serializable

/**
 * [PendingRobbingReaction] 的完整 persistence DTO。
 *
 * @property declarerId 加槓玩家的 UUID 字串。
 * @property declaredAction 已宣告的槓動作。
 * @property robbedTile 被搶槓的實體牌。
 * @property eligiblePlayerIds 可提交搶槓反應的玩家 UUID 字串集合。
 * @property responses 各玩家已提交的反應動作。
 */
@Serializable
data class PendingRobbingReactionPersistenceDto(
    val declarerId: String,
    val declaredAction: GameActionPersistenceDto,
    val robbedTile: IdentifiedTilePersistenceDto,
    val eligiblePlayerIds: Set<String>,
    val responses: Map<String, GameActionPersistenceDto>,
)
