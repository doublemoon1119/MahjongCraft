package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlin.uuid.Uuid

/** 只記錄清理服務生命週期呼叫的桌子移除通知 fake。 */
internal class RecordingTableRemovalNotifier(
    private val locations: TableLocationRegistry,
) : TableRemovalNotifier {
    /** 捕捉到的桌子識別碼。 */
    val capturedTableIds = mutableListOf<Uuid>()

    /** 開始移除的桌子識別碼。 */
    val begunTableIds = mutableListOf<Uuid>()

    /** 完成移除的桌子識別碼與額外接收者。 */
    val completed = mutableListOf<Pair<Uuid, Set<Uuid>>>()

    /** 完成移除時實際交給廣播器的完整接收者集合。 */
    val completedObserverIds = mutableListOf<Set<Uuid>>()

    /** 後續清理失敗或取消時釋放的桌子識別碼。 */
    val cancelledTableIds = mutableListOf<Uuid>()

    /** 可控制測試中的通知來源是否仍有效。 */
    var current: Boolean = true

    /** 捕捉時提供給通知 fake 的追蹤觀察者。 */
    var trackingObserverIds: Set<Uuid> = emptySet()

    /** 捕捉成功後執行，可在測試中模擬位置被重用。 */
    var onCaptured: (() -> Unit)? = null

    /** 開始移除前執行，可在測試中模擬權威清理後來源被重用。 */
    var onBeginRemoval: (() -> Unit)? = null

    override suspend fun capture(tableId: Uuid): TableRemovalNotification? {
        val entry = locations.get(tableId) ?: return null
        capturedTableIds += tableId
        val notification = TableRemovalNotification(
            ticket = FabricTableStateTicket(
                tableId = tableId,
                sessionId = SESSION_ID,
                entry = entry,
            ),
            observerIds = trackingObserverIds,
        )
        onCaptured?.invoke()
        return notification
    }

    override fun isCurrent(notification: TableRemovalNotification): Boolean = current && locations.get(notification.ticket.tableId) === notification.ticket.entry

    override fun beginRemoval(notification: TableRemovalNotification): Boolean {
        begunTableIds += notification.ticket.tableId
        onBeginRemoval?.invoke()
        return current && locations.get(notification.ticket.tableId) === notification.ticket.entry
    }

    override fun completeRemoval(notification: TableRemovalNotification, additionalObserverIds: Set<Uuid>) {
        completed += notification.ticket.tableId to additionalObserverIds
        completedObserverIds += notification.observerIds + additionalObserverIds
    }

    override fun cancelRemoval(notification: TableRemovalNotification) {
        cancelledTableIds += notification.ticket.tableId
    }

    private companion object {
        val SESSION_ID: Uuid = Uuid.random()
    }
}
