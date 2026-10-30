package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiWallProgress

/**
 * 保存本局進度與尚未公開的項目。
 *
 * @return 完整權威進度資料。
 */
fun RiichiWallProgress.toPersistenceDto(): RiichiWallProgressPersistenceDto = RiichiWallProgressPersistenceDto(
    completedSupplementalDrawCount = completedSupplementalDrawCount,
    revealedKanDoraCount = revealedKanDoraCount,
    pendingKanDoraReveals = pendingKanDoraReveals.map { it.toPersistenceDto() },
    completedNorthDrawCount = completedNorthDrawCount,
)

/**
 * 還原本局進度，不增加或重跑補牌。
 *
 * @return 待由組件定義驗證的完整權威進度。
 */
fun RiichiWallProgressPersistenceDto.toDomain(): RiichiWallProgress = RiichiWallProgress(
    completedSupplementalDrawCount = completedSupplementalDrawCount,
    revealedKanDoraCount = revealedKanDoraCount,
    pendingKanDoraReveals = pendingKanDoraReveals.map { it.toDomain() },
    completedNorthDrawCount = completedNorthDrawCount,
)
