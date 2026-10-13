package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** 驗證同一連線的歷史查詢冷卻與前景查詢優先順序。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientHistoryRequestGateTest {
    /** 前景查詢等待時，背景保存輪詢不會在冷卻結束時插隊。 */
    @Test
    fun `foreground request takes priority over archive polling`() = runTest {
        var now = Duration.ZERO
        val gate = ClientHistoryRequestGate(minimumInterval = { 100.milliseconds }, now = { now }, sessionRevision = { 0L })
        gate.awaitSendSlot(ClientHistoryRequestPriority.FOREGROUND)

        val order = mutableListOf<String>()
        val background = launch {
            gate.awaitSendSlot(ClientHistoryRequestPriority.BACKGROUND)
            order += "background"
        }
        runCurrent()
        val foreground = launch {
            gate.awaitSendSlot(ClientHistoryRequestPriority.FOREGROUND)
            order += "foreground"
        }
        runCurrent()

        now = 100.milliseconds
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("foreground"), order)

        foreground.cancel()
        background.cancel()
    }

    /** 取得名額前取消時，前景等待者會被移除，後續背景輪詢仍可送出。 */
    @Test
    fun `cancelling foreground waiter releases its priority`() = runTest {
        var now = Duration.ZERO
        val gate = ClientHistoryRequestGate(minimumInterval = { 100.milliseconds }, now = { now }, sessionRevision = { 0L })
        gate.awaitSendSlot(ClientHistoryRequestPriority.FOREGROUND)

        val foreground = launch { gate.awaitSendSlot(ClientHistoryRequestPriority.FOREGROUND) }
        runCurrent()
        foreground.cancel()
        runCurrent()

        now = 100.milliseconds
        var sent = false
        launch {
            gate.awaitSendSlot(ClientHistoryRequestPriority.BACKGROUND)
            sent = true
        }
        advanceTimeBy(100)
        runCurrent()
        assertTrue(sent)
    }

    /** 前景等待者存在時背景查詢保持阻擋，前景取得名額後背景才繼續。 */
    @Test
    fun `background remains blocked until foreground request completes`() = runTest {
        var now = Duration.ZERO
        val gate = ClientHistoryRequestGate(minimumInterval = { 100.milliseconds }, now = { now }, sessionRevision = { 0L })
        gate.awaitSendSlot(ClientHistoryRequestPriority.FOREGROUND)

        val order = mutableListOf<String>()
        val background = launch {
            gate.awaitSendSlot(ClientHistoryRequestPriority.BACKGROUND)
            order += "background"
        }
        runCurrent()
        val foreground = launch {
            gate.awaitSendSlot(ClientHistoryRequestPriority.FOREGROUND)
            order += "foreground"
        }
        runCurrent()

        now = 100.milliseconds
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("foreground"), order)

        now = 200.milliseconds
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("foreground", "background"), order)
        background.cancel()
    }

    /** 連線工作階段改變時，下一次查詢不會沿用上一個工作階段的冷卻。 */
    @Test
    fun `session revision resets the shared cooldown`() = runTest {
        var now = Duration.ZERO
        var revision = 0L
        val gate = ClientHistoryRequestGate(
            minimumInterval = { 100.milliseconds },
            now = { now },
            sessionRevision = { revision },
        )
        gate.awaitSendSlot(ClientHistoryRequestPriority.FOREGROUND)

        now = 10.milliseconds
        revision = 1L
        var sent = false
        launch {
            gate.awaitSendSlot(ClientHistoryRequestPriority.BACKGROUND)
            sent = true
        }
        runCurrent()

        assertTrue(sent)
    }

    /** 新工作階段被觀察到時，喚醒仍在等待舊冷卻的工作，避免前景等待者繼續阻擋新查詢。 */
    @Test
    fun `observing a new session wakes old foreground waiters`() = runTest {
        var now = Duration.ZERO
        var revision = 0L
        val gate = ClientHistoryRequestGate(
            minimumInterval = { 100.milliseconds },
            now = { now },
            sessionRevision = { revision },
        )
        gate.awaitSendSlot(ClientHistoryRequestPriority.FOREGROUND)
        var foregroundSent = false
        launch {
            gate.awaitSendSlot(ClientHistoryRequestPriority.FOREGROUND)
            foregroundSent = true
        }
        runCurrent()

        now = 10.milliseconds
        advanceTimeBy(10)
        revision++
        var backgroundSent = false
        launch {
            gate.awaitSendSlot(ClientHistoryRequestPriority.BACKGROUND)
            backgroundSent = true
        }
        runCurrent()
        assertTrue(foregroundSent)
        assertEquals(false, backgroundSent)

        now = 110.milliseconds
        advanceTimeBy(100)
        runCurrent()
        assertTrue(backgroundSent)
    }
}
