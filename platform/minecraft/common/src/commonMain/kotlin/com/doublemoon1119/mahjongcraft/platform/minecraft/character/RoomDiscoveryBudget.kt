package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlin.uuid.Uuid

/**
 * 同一執行緒使用的空席查詢限流，不保存位置、房間或查詢結果。
 *
 * @property intervalTicks 同一主體兩次查詢的最短間隔。
 * @property maximumPerTick 每個時刻全域接受的最大查詢數。
 * @property maximumSubjects 最多保存的主體數，須足以覆蓋整個限流期間。
 */
class RoomDiscoveryBudget(
    private val intervalTicks: Long,
    private val maximumPerTick: Int,
    private val maximumSubjects: Int,
) {
    /** 主體最近被接受的查詢時刻，不保存結果或授權。 */
    private val previous = linkedMapOf<Uuid, Long>()

    /** 目前全域計數所屬時刻。 */
    private var currentTick = Long.MIN_VALUE

    /** 同一時刻已接受數量。 */
    private var accepted = 0

    /** 目前追蹤數量，供工作量診斷使用。 */
    val trackedSubjects: Int get() = previous.size

    init {
        require(intervalTicks > 0 && maximumPerTick > 0 && maximumSubjects > 0)
        require(intervalTicks <= maximumSubjects.toLong() / maximumPerTick) { "Subject capacity must cover the complete query interval" }
    }

    /**
     * 認領一次查詢額度，拒絕時不更新主體記錄。
     *
     * @param subject 已驗證來源的穩定編號。
     * @param tick 平台提供的單調時刻，倒退時清除舊時鐘記錄。
     * @return 本次可開始有界查詢時為 true，不代表房間授權。
     */
    fun acquire(subject: Uuid, tick: Long): Boolean {
        if (tick < currentTick) reset()
        if (tick != currentTick) {
            currentTick = tick
            accepted = 0
        }
        if (accepted >= maximumPerTick) return false
        val last = previous[subject]
        if (last != null && tick - last < intervalTicks) return false
        accepted++
        previous.remove(subject)
        if (previous.size >= maximumSubjects) previous.remove(previous.keys.first())
        previous[subject] = tick
        return true
    }

    /** 結束 session 時清除主體與時鐘記錄。 */
    fun reset() {
        previous.clear()
        currentTick = Long.MIN_VALUE
        accepted = 0
    }
}
