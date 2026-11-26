package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import kotlinx.serialization.Serializable

/**
 * 日麻本局補牌與指示牌公開進度的完整保存資料。
 *
 * @property completedSupplementalDrawCount 已完成的槓後補牌次數。
 * @property revealedKanDoraCount 已公開的追加槓寶牌數量。
 * @property pendingKanDoraReveals 尚未公開的項目，保留建立順序。
 * @property completedNorthDrawCount 已完成的拔北補牌次數。
 */
@Serializable
data class RiichiWallProgressPersistenceDto(
    val completedSupplementalDrawCount: Int,
    val revealedKanDoraCount: Int,
    val pendingKanDoraReveals: List<RiichiPendingKanDoraRevealPersistenceDto>,
    val completedNorthDrawCount: Int,
)
