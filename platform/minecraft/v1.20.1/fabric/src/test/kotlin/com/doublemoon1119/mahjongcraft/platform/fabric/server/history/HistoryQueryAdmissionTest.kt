package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** 驗證准入不排隊、頻率限制及重新連線的權杖隔離。 */
class HistoryQueryAdmissionTest {
    /** 執行中的要求優先回報忙碌，完成後仍遵循間隔。 */
    @Test
    fun `test pending query and minimum interval are bounded`() {
        var instant = 0L
        val gate = HistoryQueryAdmission(now = { instant })
        val player = Uuid.random()
        val first = assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(player))
        assertIs<HistoryQueryAdmission.Admission.Busy>(gate.acquire(player))
        gate.release(player, first.token)
        assertIs<HistoryQueryAdmission.Admission.RateLimited>(gate.acquire(player))
        instant = 250.milliseconds.inWholeNanoseconds
        assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(player))
    }

    /** 舊連線的完成不得解除重新連線後的新要求。 */
    @Test
    fun `test stale release does not unlock reconnected query`() {
        val gate = HistoryQueryAdmission { 0L }
        val player = Uuid.random()
        val old = assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(player))
        gate.remove(player)
        assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(player))
        gate.release(player, old.token)
        assertIs<HistoryQueryAdmission.Admission.Busy>(gate.acquire(player))
    }

    /** 全伺服器達到上限時不建立等待佇列，完成工作後才釋放名額。 */
    @Test
    fun `test outstanding query cap is released by completion`() {
        val gate = HistoryQueryAdmission(
            limits = { HistoryQueryAdmissionLimits(maximumOutstanding = 2) },
            now = { 0L },
        )
        val firstPlayer = Uuid.random()
        val secondPlayer = Uuid.random()
        val thirdPlayer = Uuid.random()
        val first = assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(firstPlayer))
        assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(secondPlayer))
        assertIs<HistoryQueryAdmission.Admission.Busy>(gate.acquire(thirdPlayer))

        gate.remove(firstPlayer)
        assertIs<HistoryQueryAdmission.Admission.Busy>(gate.acquire(thirdPlayer))
        gate.release(firstPlayer, first.token)
        assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(thirdPlayer))
    }

    /** 忙碌與頻率拒絕回覆每位玩家各自節流，斷線後重新連線可重新通知。 */
    @Test
    fun `test rejection replies are throttled per player`() {
        var instant = 0L
        val gate = HistoryQueryAdmission(now = { instant })
        val player = Uuid.random()
        assertTrue(gate.shouldSendRejection(player))
        assertFalse(gate.shouldSendRejection(player))

        instant = 1_000.milliseconds.inWholeNanoseconds
        assertTrue(gate.shouldSendRejection(player))

        gate.remove(player)
        assertTrue(gate.shouldSendRejection(player))
    }

    /** 多人工作上限涵蓋較大的設定值，不把伺服器准入錯當單一資料庫鎖。 */
    @Test
    fun `test multiplayer caps allow configured outstanding work and reject overflow`() {
        listOf(8, 16, 32, 64).forEach { cap ->
            val gate = HistoryQueryAdmission(limits = { HistoryQueryAdmissionLimits(maximumOutstanding = cap) }, now = { 0L })
            val players = List(cap + 1) { Uuid.random() }
            val accepted = players.take(cap).map { player ->
                assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(player))
            }
            assertIs<HistoryQueryAdmission.Admission.Busy>(gate.acquire(players.last()))
            gate.release(players.first(), accepted.first().token)
            assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(players.last()))
            gate.release(players.first(), accepted.first().token)
            assertIs<HistoryQueryAdmission.Admission.Busy>(gate.acquire(Uuid.random()))
        }
    }

    /** 重新載入後降低或提高查詢工作上限只影響新准入，不取消既有工作。 */
    @Test
    fun `test outstanding limit follows reloaded snapshot`() {
        var currentLimits = HistoryQueryAdmissionLimits(maximumOutstanding = 2)
        val gate = HistoryQueryAdmission(limits = { currentLimits }, now = { 0L })
        val firstPlayer = Uuid.random()
        val secondPlayer = Uuid.random()
        val thirdPlayer = Uuid.random()
        val first = assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(firstPlayer))
        val second = assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(secondPlayer))

        currentLimits = currentLimits.copy(maximumOutstanding = 1)
        assertIs<HistoryQueryAdmission.Admission.Busy>(gate.acquire(thirdPlayer))
        gate.release(firstPlayer, first.token)
        assertIs<HistoryQueryAdmission.Admission.Busy>(gate.acquire(thirdPlayer))
        gate.release(secondPlayer, second.token)
        assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(thirdPlayer))

        currentLimits = currentLimits.copy(maximumOutstanding = 2)
        assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(Uuid.random()))
    }

    /** 重新載入後的查詢間隔會立即套用於下一次准入。 */
    @Test
    fun `test minimum interval follows reloaded snapshot`() {
        var instant = 0L
        var currentLimits = HistoryQueryAdmissionLimits(minimumInterval = 1.seconds)
        val gate = HistoryQueryAdmission(limits = { currentLimits }, now = { instant })
        val player = Uuid.random()
        val first = assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(player))
        gate.release(player, first.token)

        instant = 500.milliseconds.inWholeNanoseconds
        assertIs<HistoryQueryAdmission.Admission.RateLimited>(gate.acquire(player))
        currentLimits = currentLimits.copy(minimumInterval = 100.milliseconds)
        assertIs<HistoryQueryAdmission.Admission.Accepted>(gate.acquire(player))
    }

    /** 重新載入後的拒絕回覆間隔會立即套用於下一次通知。 */
    @Test
    fun `test rejection interval follows reloaded snapshot`() {
        var instant = 0L
        var currentLimits = HistoryQueryAdmissionLimits(rejectionResponseInterval = 1.seconds)
        val gate = HistoryQueryAdmission(limits = { currentLimits }, now = { instant })
        val player = Uuid.random()
        assertTrue(gate.shouldSendRejection(player))

        instant = 500.milliseconds.inWholeNanoseconds
        assertFalse(gate.shouldSendRejection(player))
        currentLimits = currentLimits.copy(rejectionResponseInterval = 100.milliseconds)
        assertTrue(gate.shouldSendRejection(player))
    }
}
