package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 摸牌後自動處理的逐步時間軸，以這次呈現開始的時刻為 0。
 *
 * 第一張牌先照一般摸牌落到摸牌位；之後每一步等上一張牌落地並停頓 [STEP_PAUSE_TICKS]，才把摸牌位上的牌移到移出區，
 * 移出的牌落地後再從牌牆補下一張。牌張動畫、牌牆移動、宣告音效與分數提示都依這份時間軸排定，彼此不會錯開。
 */
object DrawFollowUpTimeline {
    /** 每張牌落到摸牌位之後、開始下一步之前的停頓。 */
    const val STEP_PAUSE_TICKS: Long = 6L

    /** 一次摸牌從離開牌牆到落在摸牌位的時間。 */
    const val DRAW_DURATION_TICKS: Long = (
        MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DRAW_SNAP_GAP_TICKS +
            MahjongTileTableLayout.DRAW_DROP_DURATION_TICKS
        ).toLong()

    /**
     * 第一次摸牌開始前的延遲：同一次呈現另有牌移入副露或移出區時，等那些牌落地後才摸牌。
     *
     * @param hasClaimAnimation 這次呈現是否另有牌移入副露或移出區。
     * @return 第一次摸牌開始前的 tick 數。
     */
    fun initialDrawDelayTicks(hasClaimAnimation: Boolean): Long = if (hasClaimAnimation) MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS.toLong() else 0L

    /**
     * 每一步開始的時刻，也就是摸牌位上的牌離開、移向移出區的時刻。
     *
     * @param stepCount 自動處理的步數。
     * @param hasClaimAnimation 這次呈現是否另有牌移入副露或移出區。
     * @return 依步驟順序排列、相對於呈現開始的 tick 數。
     */
    fun stepStartTicks(stepCount: Int, hasClaimAnimation: Boolean): List<Long> {
        require(stepCount >= 0) { "Step count must not be negative" }
        var nextStart = initialDrawDelayTicks(hasClaimAnimation) + DRAW_DURATION_TICKS + STEP_PAUSE_TICKS
        return List(stepCount) {
            val start = nextStart
            nextStart = replacementDrawStartTicks(start) + DRAW_DURATION_TICKS + STEP_PAUSE_TICKS
            start
        }
    }

    /**
     * 某一步補牌開始離開牌牆的時刻：移出的牌落地之後。
     *
     * @param stepStartTicks 這一步開始的時刻。
     * @return 相對於呈現開始的 tick 數。
     */
    fun replacementDrawStartTicks(stepStartTicks: Long): Long = stepStartTicks + MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS
}
