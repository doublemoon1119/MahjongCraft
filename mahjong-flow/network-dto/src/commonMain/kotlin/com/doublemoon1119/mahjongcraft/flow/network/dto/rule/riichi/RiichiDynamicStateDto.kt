package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DynamicRuleStateDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import kotlinx.serialization.Serializable

/**
 * [RiichiDynamicState] 的完整網路 DTO。
 *
 * @property riichiStickCount 桌上立直棒數量。
 * @property completedSupplementalDrawCount 已完成的嶺上摸牌次數。
 * @property revealedKanDoraCount 已揭示的槓寶牌數量。
 * @property pendingKanDoraReveals 待揭示槓寶牌的資訊。
 * @property completedNorthDrawCount 已完成的拔北摸牌次數。
 */
@Serializable
data class RiichiDynamicStateDto(
    val riichiStickCount: Int,
    val completedSupplementalDrawCount: Int,
    val revealedKanDoraCount: Int,
    val pendingKanDoraReveals: List<RiichiPendingKanDoraRevealDto>,
    val completedNorthDrawCount: Int,
) : DynamicRuleStateDto
