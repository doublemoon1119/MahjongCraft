package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 驗證牌桌呈現忙碌追蹤器的 lease 生命週期與桌間隔離。
 *
 * 測試使用不存在的牌桌位置，因此只會命中 pending lease 判斷，不需要啟動 Minecraft 世界。
 */
class TablePresentationBusyTrackerTest {
    /** 同一桌的任一 lease 尚未完成時，牌桌必須保持忙碌。 */
    @Test
    fun `a table stays busy until every lease completes`() {
        val tracker = tracker()
        val tableId = Uuid.random()
        val first = tracker.beginPending(tableId, "deal")
        val second = tracker.beginPending(tableId, "dice")

        assertTrue(tracker.isBusy(tableId))
        first.complete()
        assertTrue(tracker.isBusy(tableId))
        second.complete()
        assertFalse(tracker.isBusy(tableId))
    }

    /** lease 重複完成必須保持冪等，不得影響同桌其他 lease。 */
    @Test
    fun `completing one lease repeatedly does not release another lease`() {
        val tracker = tracker()
        val tableId = Uuid.random()
        val first = tracker.beginPending(tableId, "deal")
        val second = tracker.beginPending(tableId, "draw")

        first.complete()
        first.complete()
        assertTrue(tracker.isBusy(tableId))

        second.complete()
        assertFalse(tracker.isBusy(tableId))
    }

    /** 清除 session 舊 token 後，舊 lease 完成不得誤刪新 session 的 token。 */
    @Test
    fun `an old lease cannot release a lease created after clearAll`() {
        val tracker = tracker()
        val tableId = Uuid.random()
        val oldLease = tracker.beginPending(tableId, "old-session")

        tracker.clearAll()
        val newLease = tracker.beginPending(tableId, "new-session")
        oldLease.complete()
        assertTrue(tracker.isBusy(tableId))

        newLease.complete()
        assertFalse(tracker.isBusy(tableId))
    }

    /** 不同牌桌的 lease 必須彼此獨立。 */
    @Test
    fun `leases on different tables are independent`() {
        val tracker = tracker()
        val firstTable = Uuid.random()
        val secondTable = Uuid.random()
        val firstLease = tracker.beginPending(firstTable, "first")
        val secondLease = tracker.beginPending(secondTable, "second")

        firstLease.complete()
        assertFalse(tracker.isBusy(firstTable))
        assertTrue(tracker.isBusy(secondTable))

        secondLease.complete()
        assertFalse(tracker.isBusy(secondTable))
    }

    /** operation 名稱必須保留在 lease 上供診斷使用。 */
    @Test
    fun `a lease retains its operation name`() {
        val tracker = tracker()
        val lease = tracker.beginPending(Uuid.random(), "opening-deal")

        assertTrue(lease.operation == "opening-deal")
        lease.complete()
    }

    /** 建立不含世界狀態的 tracker。 */
    private fun tracker(): TablePresentationBusyTracker = TablePresentationBusyTracker(FabricServerHolder(), TableLocationRegistry())
}
