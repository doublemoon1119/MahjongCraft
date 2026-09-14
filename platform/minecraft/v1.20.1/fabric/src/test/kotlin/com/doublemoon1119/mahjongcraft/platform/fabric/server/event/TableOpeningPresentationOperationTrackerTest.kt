package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [TableOpeningPresentationOperationTracker] 的世代隔離與 fail-stop 測試。 */
class TableOpeningPresentationOperationTrackerTest {
    /** 同一捕捉窗口應讓後續開局 presentation 共用相同 ticket。 */
    @Test
    fun `test capture returns current opening ticket until registration finishes`() {
        val tracker = TableOpeningPresentationOperationTracker()
        val tableId = Uuid.random()

        val ticket = tracker.begin(tableId)

        assertEquals(ticket, tracker.capture(tableId))
        assertTrue(tracker.mayRun(ticket))
        tracker.finishRegistration(ticket)
        assertNull(tracker.capture(tableId))
        assertTrue(tracker.mayRun(ticket))
    }

    /** 第一筆失敗應阻止同批後續工作，並保留原始 cause。 */
    @Test
    fun `test failure stops remaining work in same generation`() {
        val tracker = TableOpeningPresentationOperationTracker()
        val ticket = tracker.begin(Uuid.random())
        val cause = IllegalStateException("entity index failed")

        val failure = tracker.fail(ticket, "wall", cause)

        assertFalse(tracker.mayRun(ticket))
        assertEquals("wall", failure.stage)
        assertEquals(ticket, failure.ticket)
        assertSame(cause, failure.cause)
    }

    /** 新世代應使尚未執行的舊工作失效，但不繼承舊世代失敗。 */
    @Test
    fun `test new generation invalidates old work and starts healthy`() {
        val tracker = TableOpeningPresentationOperationTracker()
        val tableId = Uuid.random()
        val oldTicket = tracker.begin(tableId)
        tracker.fail(oldTicket, "wall", IllegalStateException())

        val newTicket = tracker.begin(tableId)

        assertFalse(tracker.mayRun(oldTicket))
        assertTrue(tracker.mayRun(newTicket))
        assertEquals(oldTicket.generation + 1L, newTicket.generation)
    }

    /** 一桌失敗不得阻止另一桌的開局工作。 */
    @Test
    fun `test failure is isolated by table`() {
        val tracker = TableOpeningPresentationOperationTracker()
        val failed = tracker.begin(Uuid.random())
        val healthy = tracker.begin(Uuid.random())

        tracker.fail(failed, "dice", IllegalStateException())

        assertFalse(tracker.mayRun(failed))
        assertTrue(tracker.mayRun(healthy))
    }

    /** Session 清理後所有舊 ticket 均應失效。 */
    @Test
    fun `test clear all invalidates existing tickets`() {
        val tracker = TableOpeningPresentationOperationTracker()
        val ticket = tracker.begin(Uuid.random())

        tracker.clearAll()

        assertFalse(tracker.mayRun(ticket))
        assertIs<OpeningPresentationOperationException>(tracker.fail(ticket, "wall", IllegalStateException()))
    }
}
