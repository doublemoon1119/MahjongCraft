package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlin.uuid.Uuid

/** 大部分時間看自己的手牌區，依各 AI 身分錯開短暫轉看公開目標的時機。 */
object AiCharacterLookSchedule {
    /**
     * 以公開身分及時間決定情境，不讀取手牌、可用動作或 AI 分析。
     *
     * @param playerId AI UUID。
     * @param tick 目前 session 的遊戲 tick。
     * @return 主要看自己的手牌區，偶爾短暫看捨牌或行動者。
     */
    fun focus(playerId: Uuid, tick: Long): AiCharacterLookFocus {
        val identity = playerId.toString().hashCode().toLong() and UNSIGNED_MASK
        val cycle = MIN_CYCLE_TICKS + identity % CYCLE_VARIATION
        val phase = (tick % cycle + (identity ushr PHASE_SHIFT) % cycle) % cycle
        return when {
            phase < cycle - DISCARD_TICKS - PARTICIPANT_TICKS -> AiCharacterLookFocus.HAND_AREA
            phase < cycle - PARTICIPANT_TICKS -> AiCharacterLookFocus.DISCARD
            else -> AiCharacterLookFocus.PARTICIPANT
        }
    }

    /** 身分僅用於去同步，不是遊戲亂數來源。 */
    private const val UNSIGNED_MASK: Long = 0xffffffffL

    /** 最短注視週期，約七秒。 */
    private const val MIN_CYCLE_TICKS: Long = 140L

    /** 各角色的週期差異範圍。 */
    private const val CYCLE_VARIATION: Long = 61L

    /** 相位使用雜湊的另一部分，避免與週期完全重疊。 */
    private const val PHASE_SHIFT: Int = 8

    /** 短暫看公開捨牌的 tick 數。 */
    private const val DISCARD_TICKS: Long = 18L

    /** 短暫看目前行動者的 tick 數。 */
    private const val PARTICIPANT_TICKS: Long = 12L
}
