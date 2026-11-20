package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryLimits
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto

/**
 * 回放一局時的牌面暫存：保留目前這一步前後一段範圍的牌面，並決定下一次向伺服器要哪幾步。
 *
 * 步數 0 是初始牌面，步數 k 是第 k - 1 筆交易完成後。同一時間只有一次要求在途；優先補目前這一步，
 * 其次是之後的步數，最後才是之前的步數，讓播放與往回一步都不必等待。範圍外的牌面會丟掉，暫存大小固定。
 *
 * @property stepCount 這一局的總步數，等於交易數加一。
 * @property behind 目前這一步之前保留幾步。
 * @property ahead 目前這一步之後保留幾步。
 */
internal class HistoryReplayBuffer(
    val stepCount: Int,
    private val behind: Int = DEFAULT_BEHIND,
    private val ahead: Int = DEFAULT_AHEAD,
) {
    init {
        require(stepCount >= 1) { "A replay has at least the initial state" }
        require(behind >= 0 && ahead >= 0) { "Replay buffer window must not be negative" }
    }

    /** 已取得的牌面，依步數索引。 */
    private val states = HashMap<Int, HistoryRoundStateDto>()

    /** 在途要求涵蓋的步數；沒有在途要求時為 null。 */
    private var inFlight: IntRange? = null

    /** 目前顯示的步數。 */
    var current: Int = 0
        private set

    /**
     * 取得某一步的牌面。
     *
     * @param step 步數。
     * @return 牌面；還沒取得時為 null。
     */
    fun stateAt(step: Int): HistoryRoundStateDto? = states[step]

    /**
     * 移到某一步，丟掉範圍外的牌面。
     *
     * @param step 新的步數，超出範圍時夾到第一步或最後一步。
     */
    fun moveTo(step: Int) {
        current = step.coerceIn(0, stepCount - 1)
        val window = window()
        states.keys.retainAll { it in window }
    }

    /**
     * 下一次要向伺服器要的範圍；有在途要求或範圍內都已取得時為 null。
     *
     * @return 要求的第一步與數量。
     */
    fun nextRequest(): HistoryReplayRequest? {
        if (inFlight != null) return null
        val window = window()
        val forward = (current..window.last)
        val backward = (current - 1 downTo window.first)
        val missing = (forward + backward).firstOrNull { it !in states } ?: return null
        var start = missing
        var end = missing
        if (missing >= current) {
            while (end + 1 <= window.last && end + 1 - start + 1 <= HistoryQueryLimits.MAX_ROUND_STATES && end + 1 !in states) end++
        } else {
            // 往前缺的步數從最近的一步往回延伸成一段，一次補齊。
            while (start - 1 >= window.first && end - (start - 1) + 1 <= HistoryQueryLimits.MAX_ROUND_STATES && start - 1 !in states) start--
        }
        return HistoryReplayRequest(start, end - start + 1)
    }

    /**
     * 記下已送出的要求，在回覆或失敗之前不再送出新的要求。
     *
     * @param request 已送出的要求。
     */
    fun markRequested(request: HistoryReplayRequest) {
        inFlight = request.start until request.start + request.count
    }

    /**
     * 收下伺服器回覆的連續牌面，只保留目前範圍內的步數。
     *
     * @param start 回覆的第一步。
     * @param received 依步數先後排列的牌面。
     */
    fun accept(start: Int, received: List<HistoryRoundStateDto>) {
        inFlight = null
        val window = window()
        received.forEachIndexed { offset, state ->
            val step = start + offset
            if (step in window) states[step] = state
        }
    }

    /** 要求失敗或在送出前取消，之後可以重新要求同一段範圍。 */
    fun releaseRequest() {
        inFlight = null
    }

    /**
     * 目前保留的步數範圍。
     *
     * @return 包含目前這一步的範圍。
     */
    private fun window(): IntRange = (current - behind).coerceAtLeast(0)..(current + ahead).coerceAtMost(stepCount - 1)

    /** 預設範圍與步數換算。 */
    companion object {
        /** 預設在目前這一步之前保留的步數。 */
        const val DEFAULT_BEHIND: Int = 8

        /** 預設在目前這一步之後保留的步數。 */
        const val DEFAULT_AHEAD: Int = 32

        /**
         * 步數對應的局內位置。
         *
         * @param step 步數。
         * @return 初始位置或某一筆交易之後。
         */
        fun positionOf(step: Int): HistoryRoundPositionDto = if (step == 0) HistoryRoundPositionDto.Initial else HistoryRoundPositionDto.AfterTransaction(step - 1)
    }
}

/**
 * 回放暫存要向伺服器要的一段連續步數。
 *
 * @property start 第一步。
 * @property count 步數，最多 [HistoryQueryLimits.MAX_ROUND_STATES]。
 */
internal data class HistoryReplayRequest(val start: Int, val count: Int)
