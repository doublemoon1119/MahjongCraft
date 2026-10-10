package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 尚未正式公開的日麻槓寶牌項目。
 *
 * @property actorPlayerId 宣告來源槓牌的玩家 ID。
 * @property sourceKanType 來源槓牌種類。
 * @property supplementalDrawNumber 此槓完成的是本局第幾次補牌，從一開始計數。
 */
data class RiichiPendingKanDoraReveal(
    val actorPlayerId: Uuid,
    val sourceKanType: GameAction.KanType,
    val supplementalDrawNumber: Int,
)
