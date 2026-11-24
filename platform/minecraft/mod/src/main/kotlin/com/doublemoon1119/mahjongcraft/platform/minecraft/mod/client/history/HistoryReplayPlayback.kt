package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 回放的播放狀態：播放或暫停、速度、每一步停多久，以及交易列表是否跟著目前這一步。
 *
 * 不保存步數與牌面；畫面每一幀把目前步數交給 [advance]，收到新的步數時再交給讀取端移動。
 * 有標記的交易（副露、宣告、和牌）停兩倍時間；下一步牌面還沒到時原地等待，不跳過。
 */
internal class HistoryReplayPlayback {
    /** 是否正在播放。 */
    var playing: Boolean = false
        private set

    /** 播放速度。 */
    var speed: HistoryReplaySpeed = HistoryReplaySpeed.NORMAL

    /** 交易列表是否跟著目前這一步捲動。 */
    var following: Boolean = true
        private set

    /** 目前這一步開始停留的時間。 */
    private var stepStartedAt: Duration = Duration.ZERO

    /**
     * 開始播放；已在最後一步時從頭開始。
     *
     * @param step 目前步數。
     * @param stepCount 這一局的總步數。
     * @param now 目前時間。
     * @return 開始播放的步數。
     */
    fun play(step: Int, stepCount: Int, now: Duration): Int {
        playing = true
        following = true
        stepStartedAt = now
        return if (step >= stepCount - 1) 0 else step
    }

    /** 暫停播放，停在目前這一步。 */
    fun pause() {
        playing = false
    }

    /**
     * 玩家換到別的步數（上一筆、下一筆、第一筆、最後一筆、點列表或拖進度條）；播放中會重新計時，列表恢復跟隨。
     *
     * @param now 目前時間。
     */
    fun moved(now: Duration) {
        following = true
        stepStartedAt = now
    }

    /**
     * 牌還在動畫中：這一步的停留從動畫播完才開始計算。
     *
     * @param now 目前時間。
     */
    fun hold(now: Duration) {
        stepStartedAt = now
    }

    /** 玩家自己捲動交易列表，列表停止跟隨，直到下一次播放或換步。 */
    fun scrolled() {
        following = false
    }

    /** 換到另一局：暫停，列表恢復跟隨。 */
    fun roundChanged() {
        playing = false
        following = true
    }

    /**
     * 播放中時間到了就前進一步；到最後一步時自動暫停。
     *
     * @param step 目前步數。
     * @param stepCount 這一局的總步數。
     * @param now 目前時間。
     * @param nextReady 下一步的牌面是否已經取得。
     * @param marked 目前這一步的交易是否有回放標記。
     * @return 要前進到的步數；還不用前進時為 null。
     */
    fun advance(step: Int, stepCount: Int, now: Duration, nextReady: Boolean, marked: Boolean): Int? {
        if (!playing) return null
        if (step >= stepCount - 1) {
            playing = false
            return null
        }
        if (now - stepStartedAt < dwell(marked) || !nextReady) return null
        stepStartedAt = now
        val next = step + 1
        if (next >= stepCount - 1) playing = false
        return next
    }

    /**
     * 目前速度下一步要停多久。
     *
     * @param marked 這一步的交易是否有回放標記。
     * @return 停留時間。
     */
    fun dwell(marked: Boolean): Duration = STEP_DURATION / speed.multiplier * (if (marked) MARKED_DWELL_FACTOR else 1)

    /** 停留時間設定。 */
    companion object {
        /** 一倍速時一步停留的時間。 */
        val STEP_DURATION: Duration = 1.seconds

        /** 有標記的交易停留時間的倍數。 */
        const val MARKED_DWELL_FACTOR: Int = 2
    }
}
