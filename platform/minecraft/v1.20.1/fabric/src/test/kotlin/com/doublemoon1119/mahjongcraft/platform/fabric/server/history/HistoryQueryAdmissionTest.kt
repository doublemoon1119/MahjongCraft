package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/** 驗證准入不排隊、頻率限制及重新連線的權杖隔離。 */
class HistoryQueryAdmissionTest {
    /** 執行中的要求優先回報忙碌，完成後仍遵循間隔。 */
    @Test
    fun `test pending query and minimum interval are bounded`() {
        var instant = 0L
        val gate = HistoryQueryAdmission { instant }
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
}
