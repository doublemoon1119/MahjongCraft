package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import kotlinx.serialization.Serializable

/**
 * 尚未正式公開的日麻槓寶牌網路 DTO。
 *
 * @property actorPlayerId 觸發槓的玩家 UUID 字串。
 * @property sourceKanType 觸發揭示的槓類型。
 * @property supplementalDrawNumber 該局中的補摸序號。
 */
@Serializable
data class RiichiPendingKanDoraRevealDto(
    val actorPlayerId: String,
    val sourceKanType: RiichiPendingKanTypeDto,
    val supplementalDrawNumber: Int,
)
