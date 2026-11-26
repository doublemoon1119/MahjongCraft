package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import kotlinx.serialization.Serializable

/**
 * [PendingReaction] 的完整 persistence DTO。
 *
 * @property discarderId 捨牌者的玩家 UUID 字串。
 * @property tileId 被捨出的牌張 UUID 字串。
 * @property eligiblePlayerIds 可提交反應的玩家 UUID 字串集合。
 * @property ronPlayerIds 這個視窗允許榮和的玩家 UUID 字串集合。
 * @property responses 各玩家已提交的反應動作。
 * @property id 本次反應視窗的 UUID 字串。
 * @property selectedTileIds 各玩家已提交的實體選牌 UUID 字串。
 */
@Serializable
data class PendingReactionPersistenceDto(
    val discarderId: String,
    val tileId: String,
    val eligiblePlayerIds: Set<String>,
    val ronPlayerIds: Set<String>,
    val responses: Map<String, GameActionPersistenceDto>,
    val id: String,
    val selectedTileIds: Map<String, List<String>>,
)
