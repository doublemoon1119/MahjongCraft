package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 多位玩家依序完成的牌區變化的時間軸，以這次呈現開始的時刻為 0。
 *
 * 每一步先讓移出的牌飛到移出區，落地後依序摸入每一張牌；最後一張摸入的牌落地並停頓 [DrawFollowUpTimeline.STEP_PAUSE_TICKS]
 * 後才開始下一步，跨玩家也一樣。
 */
object PlayerAreaSequenceTimeline {
    /**
     * 每一步開始的時刻。
     *
     * @param steps 依呈現順序排列的每一步移出張數與摸入張數。
     * @return 依步驟順序排列、相對於呈現開始的 tick 數。
     */
    fun stepStartTicks(steps: List<Pair<Int, Int>>): List<Long> {
        var nextStart = 0L
        return steps.map { (setAsideCount, drawnCount) ->
            require(setAsideCount >= 0 && drawnCount >= 0) { "Tile counts must not be negative" }
            val start = nextStart
            nextStart = drawStartTicks(start, hasSetAside = setAsideCount > 0, index = drawnCount) + DrawFollowUpTimeline.STEP_PAUSE_TICKS
            start
        }
    }

    /**
     * 一步中第 [index] 張摸入的牌開始離開牌牆的時刻；[index] 等於摸入張數時是這一步所有牌落地的時刻。
     *
     * @param stepStartTicks 這一步開始的時刻。
     * @param hasSetAside 這一步是否有移出的牌。
     * @param index 摸入牌的索引。
     * @return 相對於呈現開始的 tick 數。
     */
    fun drawStartTicks(stepStartTicks: Long, hasSetAside: Boolean, index: Int): Long {
        val afterSetAside = if (hasSetAside) MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS.toLong() else 0L
        return stepStartTicks + afterSetAside + index * DrawFollowUpTimeline.DRAW_DURATION_TICKS
    }
}
