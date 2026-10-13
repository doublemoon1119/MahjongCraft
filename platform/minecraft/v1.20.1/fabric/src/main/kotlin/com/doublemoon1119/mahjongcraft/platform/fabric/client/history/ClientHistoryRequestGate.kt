package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/** 歷史查詢的前景與背景送出優先級。 */
internal enum class ClientHistoryRequestPriority {
    /** 玩家目前操作的列表、摘要與牌面查詢。 */
    FOREGROUND,

    /** 畫面等待保存狀態的背景輪詢。 */
    BACKGROUND,
}

/**
 * 同一 client 連線共用的歷史查詢送出冷卻；前景查詢與保存狀態輪詢不可各自繞過它。
 *
 * @property minimumInterval 目前連線公布的最短查詢間隔。
 * @property now 單調時間來源。
 * @property sessionRevision 目前 client 連線工作階段版本。
 */
internal class ClientHistoryRequestGate(
    private val minimumInterval: () -> Duration,
    private val now: () -> Duration,
    private val sessionRevision: () -> Long,
) {
    /** 保護最近送出時間，避免多個 controller 同時通過冷卻檢查。 */
    private val mutex = Mutex()

    /** 最近一次任一歷史查詢送出的單調時間。 */
    private var lastSentAt: Duration? = null

    /** 正在等待送出的前景查詢數量；背景輪詢不得插隊。 */
    private var foregroundWaiters: Int = 0

    /** 最近觀察到的連線工作階段版本。 */
    private var observedSessionRevision: Long = sessionRevision()

    /** 查詢狀態改變時喚醒等待者，避免背景輪詢忙等。 */
    private val signal = MutableStateFlow(0L)

    /**
     * 等待同一連線的共同冷卻結束，並原子地登記本次送出時間。
     *
     * @param priority 本次查詢的前景或背景優先級。
     */
    suspend fun awaitSendSlot(priority: ClientHistoryRequestPriority) {
        mutex.withLock {
            val revision = sessionRevision()
            if (revision != observedSessionRevision) {
                observedSessionRevision = revision
                lastSentAt = null
                signal.value++
            }
        }
        if (priority == ClientHistoryRequestPriority.FOREGROUND) {
            mutex.withLock { foregroundWaiters++ }
        }
        try {
            while (true) {
                val observedSignal = signal.value
                val (blocked, wait) = mutex.withLock {
                    if (priority == ClientHistoryRequestPriority.BACKGROUND && foregroundWaiters > 0) {
                        true to null
                    } else {
                        val remaining = (lastSentAt?.plus(minimumInterval()) ?: now()) - now()
                        if (remaining <= Duration.ZERO) {
                            lastSentAt = now()
                            false to null
                        } else {
                            false to remaining
                        }
                    }
                }
                if (blocked) {
                    signal.first { it != observedSignal }
                } else if (wait == null || wait <= Duration.ZERO) {
                    return
                } else {
                    withTimeoutOrNull(wait.inWholeMilliseconds.coerceAtLeast(1)) {
                        signal.first { it != observedSignal }
                    }
                }
            }
        } finally {
            if (priority == ClientHistoryRequestPriority.FOREGROUND) {
                withContext(NonCancellable) {
                    mutex.withLock {
                        foregroundWaiters--
                        signal.value++
                    }
                }
            }
        }
    }
}
