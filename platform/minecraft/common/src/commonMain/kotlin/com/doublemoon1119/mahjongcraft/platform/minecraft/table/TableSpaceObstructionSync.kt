package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.uuid.Uuid

/**
 * 決定每次檢查後要把哪些擺牌層標示送給哪些玩家。
 *
 * 每張桌子記住最後一次送出的標示與收件玩家：內容改變時送給所有收件玩家，否則只送給新加入的玩家；
 * 不再收件的玩家收到清除。沒有擋住、也從未送過標示的桌子不送任何東西。
 */
class TableSpaceObstructionSync {
    /** 每張桌子最後一次送出的標示與收件玩家。 */
    private val published = mutableMapOf<Uuid, Published>()

    /**
     * 套用一張等待開局桌子這次的檢查結果。
     *
     * @param tableId 桌子 UUID。
     * @param payload 這次檢查結果的標示。
     * @param recipientIds 目前應該看到標示的玩家。
     * @return 這次要送出的標示。
     */
    fun update(
        tableId: Uuid,
        payload: TableSpaceObstructionPayloadDto,
        recipientIds: Set<Uuid>,
    ): List<TableSpaceObstructionDelivery> {
        val previous = published[tableId]
        if (previous == null && payload.cells.isEmpty()) return emptyList()
        val contentChanged = previous == null || previous.payload != payload
        val deliveries = buildList {
            previous?.let { sent ->
                val cleared = sent.payload.copy(cells = emptyList())
                (sent.recipientIds - recipientIds).forEach { add(TableSpaceObstructionDelivery(it, cleared)) }
            }
            recipientIds.forEach { recipientId ->
                if (contentChanged || recipientId !in previous.recipientIds) {
                    add(TableSpaceObstructionDelivery(recipientId, payload))
                }
            }
        }
        if (payload.cells.isEmpty()) published.remove(tableId) else published[tableId] = Published(payload, recipientIds)
        return deliveries
    }

    /**
     * 清除已不再等待開局的桌子，對最後一次收到標示的玩家送出清除。
     *
     * @param waitingTableIds 目前仍在等待開局的桌子。
     * @return 這次要送出的清除。
     */
    fun retain(waitingTableIds: Set<Uuid>): List<TableSpaceObstructionDelivery> = (published.keys - waitingTableIds).toList().flatMap(::clear)

    /**
     * 清除一張桌子的標示，對最後一次收到標示的玩家送出清除。
     *
     * @param tableId 桌子 UUID。
     * @return 這次要送出的清除；從未送過標示時為空。
     */
    fun clear(tableId: Uuid): List<TableSpaceObstructionDelivery> {
        val previous = published.remove(tableId) ?: return emptyList()
        val cleared = previous.payload.copy(cells = emptyList())
        return previous.recipientIds.map { TableSpaceObstructionDelivery(it, cleared) }
    }

    /** 忘記所有已送出的標示，例如伺服器 session 結束時；客戶端斷線時自行清除。 */
    fun reset() {
        published.clear()
    }

    /**
     * 最後一次送出的標示。
     *
     * @property payload 送出的內容。
     * @property recipientIds 收到這份內容的玩家。
     */
    private data class Published(
        val payload: TableSpaceObstructionPayloadDto,
        val recipientIds: Set<Uuid>,
    )
}
