package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.observer.ObserverSnapshotBroadcaster
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/**
 * 桌子清理前保留追蹤者，清理後將失效請求交給共用廣播器。
 *
 * @property delivery 桌況與移除通知的交付來源檢查。
 * @property environment 已發布位置與主執行緒追蹤查詢。
 * @property broadcaster 保留先前快照收件者並序列交付失效通知。
 * @property scope 目前 server session 的工作作用域。
 * @property dispatchers 伺服器主執行緒排程器。
 */
internal class FabricTableRemovalNotifier(
    private val delivery: FabricTableStateDelivery,
    private val environment: TableStateDeliveryEnvironment,
    private val broadcaster: ObserverSnapshotBroadcaster,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) : TableRemovalNotifier {
    /** 記錄已完成權威清理但未能交付的通知，不回復已提交狀態。 */
    private val logger = mahjongCraftLogger(FabricTableRemovalNotifier::class)

    override suspend fun capture(tableId: Uuid): TableRemovalNotification? = withContext(dispatchers.main) {
        val ticket = delivery.capture(tableId) ?: return@withContext null
        TableRemovalNotification(ticket, environment.trackingObservers(ticket.entry))
    }

    override fun isCurrent(notification: TableRemovalNotification): Boolean = delivery.hasSameSource(notification.ticket)

    override fun beginRemoval(notification: TableRemovalNotification): Boolean = delivery.beginRemoval(notification.ticket)

    override fun completeRemoval(notification: TableRemovalNotification, additionalObserverIds: Set<Uuid>) {
        val ticket = notification.ticket
        broadcaster.requestVenueInvalidation(
            id = ticket.tableId,
            additionalObserverIds = notification.observerIds + additionalObserverIds,
            requestId = ticket.requestId,
            isStillValid = { delivery.isRemoved(ticket) },
        )
        val job = scope.launch(dispatchers.main) {
            try {
                broadcaster.broadcast()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logger.error("Table removal delivery failed for table {} request {}", ticket.tableId, ticket.requestId, failure)
            }
        }
        job.invokeOnCompletion { delivery.finishRemoval(ticket) }
    }

    override fun cancelRemoval(notification: TableRemovalNotification) {
        delivery.finishRemoval(notification.ticket)
    }
}
