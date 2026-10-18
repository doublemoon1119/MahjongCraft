package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

/**
 * 描述同一局多輪擲骰在桌面上的連續時間區段。
 *
 * 每個區段都包含該輪從開始呈現到結果展示結束的時間；下一輪會在前一輪結束後才開始。
 * 時間以相對於整批呈現開始時的遊戲 tick 表示，第一輪可以包含牌牆落地等前置等待。
 *
 * @property startOffsetTicks 這一輪開始前經過的 tick 數。
 * @property endOffsetTicks 這一輪結果展示結束前經過的 tick 數。
 */
data class MahjongDiceRollTimelineSegment(
    val startOffsetTicks: Int,
    val endOffsetTicks: Int,
) {
    init {
        require(startOffsetTicks >= 0) { "Timeline start offset must not be negative" }
        require(endOffsetTicks >= startOffsetTicks) { "Timeline end offset must not precede its start" }
    }
}
