package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/** 胡牌張放置並引爆 TNT 的役滿演出唯一時間線來源。 */
object WinCelebrationCinematicTimeline {
    /** 役滿展示準備開始時間。 */
    const val PREPARATION_START = 0.0

    /** 手牌開始起飛時間。 */
    const val LAUNCH_START = 48.0

    /** 手牌開始建立環繞編隊時間。 */
    const val ORBIT_BUILDUP_START = 80.0

    /** 和牌張開始接近 TNT 放置位置時間。 */
    const val TNT_APPROACH_START = 124.0

    /** 和牌張完成 TNT 放置交接時間。 */
    const val TNT_PLACEMENT_START = 148.0

    /** 和牌張手持物由 TNT 切換為打火石時間。 */
    const val TOOL_SWAP_START = 158.0

    /** 點燃 TNT 時間。 */
    const val IGNITION_START = 166.0

    /** 引信階段開始時間。 */
    const val FUSE_START = 174.0

    /** TNT 爆炸時間。 */
    const val EXPLOSION_START = 212.0

    /** 手牌開始依序返回展示編隊時間。 */
    const val FORMATION_RETURN_START = 214.0

    /** 標題開始揭曉時間。 */
    const val TITLE_REVEAL_START = 276.0

    /** 役種展示正式開始時間。 */
    const val SHOWCASE_START = 300.0

    /** 展示結束淡出所需的 tick 數。 */
    const val FADE_OUT_TICKS = 18.0

    /** 煙火發射時間。 */
    const val FIREWORK_LAUNCH_TICK = 56L

    /** TNT 放置音效時間。 */
    const val TNT_PLACEMENT_SOUND_TICK = 153L

    /** TNT 點燃音效時間。 */
    const val IGNITION_SOUND_TICK = 170L

    /** TNT 爆炸音效時間。 */
    const val EXPLOSION_SOUND_TICK = 212L

    /** 標題揭曉音效時間。 */
    const val TITLE_REVEAL_SOUND_TICK = 286L

    /**
     * 爆風後在六 ticks 內依展示順位依序脫離，再保留四十 ticks 飛往展示位置。
     *
     * @param rank 牌在展示編隊中的順位索引。
     * @param totalCards 展示牌張總數。
     * @return 此牌開始返回編隊的 tick；牌數不超過一張時直接返回共同起始時間。
     */
    fun returnStartTick(rank: Int, totalCards: Int): Double {
        if (totalCards <= 1) return FORMATION_RETURN_START
        return FORMATION_RETURN_START + rank.coerceIn(0, totalCards - 1) * 6.0 / (totalCards - 1)
    }

    /**
     * 計算胡牌演出從準備開始到淡出結束的總長度。
     *
     * @param showcaseDurationTicks 胡牌牌型展示階段的 tick 數。
     * @return 包含展示階段與淡出的總 tick 數。
     */
    fun totalDurationTicks(showcaseDurationTicks: Int): Long = SHOWCASE_START.toLong() + showcaseDurationTicks + FADE_OUT_TICKS.toLong()
}
