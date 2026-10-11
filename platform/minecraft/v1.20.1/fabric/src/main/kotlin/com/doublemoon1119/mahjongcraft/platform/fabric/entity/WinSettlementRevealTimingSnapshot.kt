package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 和牌結算揭示動畫的時間快照。
 *
 * @property initialFadeTicks 初始淡入所需 tick 數。
 * @property entryStaggerTicks 逐條詳情揭示的間隔 tick 數。
 * @property scoreRevealTicks 分數揭示所需 tick 數。
 * @property readingTicks 保留閱讀的 tick 數。
 */
data class WinSettlementRevealTimingSnapshot(
    val initialFadeTicks: Int,
    val entryStaggerTicks: Int,
    val scoreRevealTicks: Int,
    val readingTicks: Int,
)
