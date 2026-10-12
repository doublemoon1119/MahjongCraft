package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 流局與胡牌結算共用的原版音效節奏，避免兩種面板產生不同操作語言。
 *
 * @property EVENT_GRACE_TICKS 排定音效後仍接受播放的 tick 寬限。
 * @property ROW_VOLUME 結算玩家列揭曉音量。
 * @property ROW_BASE_PITCH 第一列揭曉音高。
 * @property ROW_PITCH_STEP 後續玩家列逐列增加的音高。
 * @property DETAIL_VOLUME 等待牌資訊列揭曉音量。
 * @property DETAIL_BASE_PITCH 第一列資訊揭曉音高。
 * @property DETAIL_PITCH_STEP 後續資訊列逐列增加的音高。
 * @property TOTAL_SCORE_VOLUME 總分播放音量。
 * @property RANKING_SETTLED_VOLUME 排行落定音量。
 * @property RANKING_SETTLED_PITCH 排行落定音高。
 * @property TOTAL_SCORE_MELODY_INTERVAL_TICKS 總分旋律音符間隔。
 * @property TOTAL_SCORE_MELODY_PITCHES 總分旋律依序使用的音高。
 */
object SettlementPresentationSoundSpec {
    const val EVENT_GRACE_TICKS = 1L
    const val ROW_VOLUME = 0.25f
    const val ROW_BASE_PITCH = 0.85f
    const val ROW_PITCH_STEP = 0.1f
    const val DETAIL_VOLUME = 0.18f
    const val DETAIL_BASE_PITCH = 1.05f
    const val DETAIL_PITCH_STEP = 0.08f
    const val TOTAL_SCORE_VOLUME = 0.2f
    const val RANKING_SETTLED_VOLUME = 0.12f
    const val RANKING_SETTLED_PITCH = 0.9f
    const val TOTAL_SCORE_MELODY_INTERVAL_TICKS = 4L
    val TOTAL_SCORE_MELODY_PITCHES = listOf(0.60f, 0.76f, 0.90f, 1.20f)
}
