package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import kotlin.uuid.Uuid

/**
 * 桌子清理前捕捉的通知來源與追蹤者，不在清理後重新查原生位置。
 *
 * @property ticket 原桌子與 session 身分。
 * @param observerIds 清理前已確認追蹤原位置的玩家。
 */
class TableRemovalNotification(
    val ticket: FabricTableStateTicket,
    observerIds: Set<Uuid>,
) {
    /** 不與呼叫端可變集合共用的接收者快照。 */
    val observerIds: Set<Uuid> = observerIds.toSet()
}
