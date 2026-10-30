package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType

/** 日麻桌況攜帶的獨立規則資料。 */
object RiichiTableComponents {
    /** 場上供託的實際點數，跨局保留；收款後歸零。 */
    val POT_POINTS: ComponentType<Int> = ComponentType(
        id = "mahjongcraft:riichi/table/pot_points",
        valueClass = Int::class,
        copyValue = { it },
        validateValue = { require(it >= 0 && it % RIICHI_STICK_POINTS == 0) { "Invalid riichi pot points" } },
        target = BuiltInComponentOwners.TABLE,
    )

    /** 本局補牌與指示牌公開進度，換局時重新建立。 */
    val WALL_PROGRESS: ComponentType<RiichiWallProgress> = ComponentType(
        id = "mahjongcraft:riichi/table/wall_progress",
        valueClass = RiichiWallProgress::class,
        copyValue = { it.copy(pendingKanDoraReveals = it.pendingKanDoraReveals.toList()) },
        validateValue = { progress ->
            require(progress.completedSupplementalDrawCount in 0..4) { "Invalid kan draw count" }
            require(progress.completedNorthDrawCount in 0..4) { "Invalid north draw count" }
            require(progress.revealedKanDoraCount in 0..progress.completedSupplementalDrawCount) { "Invalid indicator count" }
            val pending = progress.pendingKanDoraReveals
            require(pending.map { it.supplementalDrawNumber }.distinct().size == pending.size) { "Duplicate pending indicator" }
            require(pending.all { it.supplementalDrawNumber in (progress.revealedKanDoraCount + 1)..progress.completedSupplementalDrawCount }) {
                "Pending indicator lies outside completed kan draws"
            }
        },
        target = BuiltInComponentOwners.TABLE,
    )
}
