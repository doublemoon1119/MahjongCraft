package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import kotlin.time.Duration.Companion.nanoseconds
import kotlin.uuid.Uuid

/**
 * 每連線的有界查詢准入；完成只解除原權杖，不影響重新連線後的新要求。
 *
 * @property limits 每次接受或判斷拒絕回覆時讀取目前設定的限制。
 * @property now 供間隔判斷的單調奈秒來源；放在最後以便正式呼叫端只提供設定來源。
 */
internal class HistoryQueryAdmission(
    private val limits: () -> HistoryQueryAdmissionLimits = { HistoryQueryAdmissionLimits() },
    private val now: () -> Long = System::nanoTime,
) {
    /** 已接受的要求與上次接受時間；玩家離線時只移除連線索引。 */
    private val entries = mutableMapOf<Uuid, Entry>()

    /** 所有仍可能執行中的工作權杖；不受玩家斷線或重新連線影響。 */
    private val activeTokens = mutableSetOf<Uuid>()

    /** 最近一次向各玩家傳送准入拒絕回覆的時間。 */
    private val rejectionResponses = mutableMapOf<Uuid, Long>()

    /**
     * 一個連線的頻率與工作狀態。
     *
     * @property acceptedAt 上次接受要求的單調奈秒。
     * @property token 目前工作權杖；null 表示已完成。
     */
    private data class Entry(val acceptedAt: Long, var token: Uuid?)

    /**
     * 嘗試建立唯一待執行要求。
     *
     * @param playerId 已驗證連線玩家。
     * @return 接受權杖或拒絕原因。
     */
    @Synchronized
    fun acquire(playerId: Uuid): Admission {
        val currentLimits = limits()
        val previous = entries[playerId]
        if (previous?.token != null) return Admission.Busy
        val instant = now()
        if (previous != null && (instant - previous.acceptedAt).nanoseconds < currentLimits.minimumInterval) return Admission.RateLimited
        if (activeTokens.size >= currentLimits.maximumOutstanding) return Admission.Busy
        val token = Uuid.random()
        entries[playerId] = Entry(instant, token)
        activeTokens += token
        return Admission.Accepted(token)
    }

    /**
     * 解除同一連線的原工作，不讓舊工作解除新要求。
     *
     * @param playerId 工作所屬玩家。
     * @param token 接受時的權杖。
     */
    @Synchronized
    fun release(playerId: Uuid, token: Uuid) {
        if (activeTokens.remove(token)) {
            entries[playerId]?.takeIf { it.token == token }?.token = null
        }
    }

    /**
     * 清除離線連線的准入記錄。
     *
     * @param playerId 已離線玩家。
     */
    @Synchronized
    fun remove(playerId: Uuid) {
        entries.remove(playerId)
        rejectionResponses.remove(playerId)
    }

    /** 判斷是否可以向玩家傳送一次准入拒絕回覆，避免拒絕封包形成回覆洪水。
     *
     * @param playerId 已驗證連線玩家。
     * @return 已超過拒絕回覆間隔時為 true，並保留此次回覆時間。
     */
    @Synchronized
    fun shouldSendRejection(playerId: Uuid): Boolean {
        val currentLimits = limits()
        val instant = now()
        val previous = rejectionResponses[playerId]
        if (previous != null && (instant - previous).nanoseconds < currentLimits.rejectionResponseInterval) return false
        rejectionResponses[playerId] = instant
        return true
    }

    /** 單次准入的結果，不包含待執行佇列。 */
    sealed interface Admission {
        /**
         * 已取得唯一工作資格。
         *
         * @property token 解除資格時必須配對的權杖。
         */
        data class Accepted(val token: Uuid) : Admission

        /** 原要求仍在執行。 */
        data object Busy : Admission

        /** 與上次接受要求的間隔過短。 */
        data object RateLimited : Admission
    }
}
