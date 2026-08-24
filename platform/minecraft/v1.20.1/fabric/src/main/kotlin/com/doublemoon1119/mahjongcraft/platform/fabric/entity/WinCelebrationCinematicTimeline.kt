package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/** 胡牌張放置並引爆 TNT 的役滿演出唯一時間線來源。 */
object WinCelebrationCinematicTimeline {
    const val PREPARATION_START = 0.0
    const val LAUNCH_START = 48.0
    const val ORBIT_BUILDUP_START = 80.0
    const val TNT_APPROACH_START = 124.0
    const val TNT_PLACEMENT_START = 148.0
    const val TOOL_SWAP_START = 158.0
    const val IGNITION_START = 166.0
    const val FUSE_START = 174.0
    const val EXPLOSION_START = 212.0
    const val FORMATION_RETURN_START = 214.0
    const val TITLE_REVEAL_START = 276.0
    const val SHOWCASE_START = 300.0
    const val FADE_OUT_TICKS = 18.0

    const val FIREWORK_LAUNCH_TICK = 56L
    const val TNT_PLACEMENT_SOUND_TICK = 153L
    const val IGNITION_SOUND_TICK = 170L
    const val EXPLOSION_SOUND_TICK = 212L
    const val TITLE_REVEAL_SOUND_TICK = 286L

    /** 爆風後在六 ticks 內依畫面順位依序脫離，再保留四十 ticks 飛往展示位置。 */
    fun returnStartTick(rank: Int, totalCards: Int): Double {
        if (totalCards <= 1) return FORMATION_RETURN_START
        return FORMATION_RETURN_START + rank.coerceIn(0, totalCards - 1) * 6.0 / (totalCards - 1)
    }

    fun totalDurationTicks(showcaseDurationTicks: Int): Long = SHOWCASE_START.toLong() + showcaseDurationTicks + FADE_OUT_TICKS.toLong()
}
