package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import kotlinx.serialization.Serializable

/**
 * [RiichiDynamicState] 的完整 persistence DTO。
 *
 * @property riichiStickCount 立直棒場供的數量。
 * @property completedSupplementalDrawCount 已完成補充摸牌的次數。
 * @property revealedKanDoraCount 已公開的槓寶牌數量。
 * @property pendingKanDoraReveals 尚未公開的槓寶牌列表。
 * @property completedNorthDrawCount 已完成拔北摸牌的次數。
 */
@Serializable
data class RiichiDynamicStatePersistenceDto(
    val riichiStickCount: Int,
    val completedSupplementalDrawCount: Int,
    val revealedKanDoraCount: Int,
    val pendingKanDoraReveals: List<RiichiPendingKanDoraRevealPersistenceDto>,
    val completedNorthDrawCount: Int,
)
