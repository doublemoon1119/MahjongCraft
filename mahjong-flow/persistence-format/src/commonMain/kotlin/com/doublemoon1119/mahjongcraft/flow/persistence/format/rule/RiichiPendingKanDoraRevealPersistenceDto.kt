package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import kotlinx.serialization.Serializable

/**
 * 尚未正式公開的日麻槓寶牌 persistence DTO。
 *
 * @property actorPlayerId 執行者的玩家 UUID 字串。
 * @property sourceKanType 觸發流程的來源槓種類。
 * @property supplementalDrawNumber 補充摸牌的序號。
 */
@Serializable
data class RiichiPendingKanDoraRevealPersistenceDto(
    val actorPlayerId: String,
    val sourceKanType: RiichiPendingKanTypePersistenceDto,
    val supplementalDrawNumber: Int,
)
