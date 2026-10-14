package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlin.uuid.Uuid

/**
 * 不依賴 Minecraft 世界的桌況交付環境替身。
 *
 * 測試可直接切換 session、桌子來源、位置索引與追蹤中的玩家，重現清理和延遲工作之間的競態。
 *
 * @property locations 測試用的桌子位置索引。
 * @property sessionId 目前 server session 的識別碼；設為 null 代表尚未啟動。
 * @property sourceExists 權威桌子來源是否仍存在。
 * @property trackingObserverIds 清理前捕捉到的追蹤玩家。
 */
internal class FakeTableStateDeliveryEnvironment(
    val locations: TableLocationRegistry = TableLocationRegistry(),
    override var sessionId: Uuid? = Uuid.random(),
    var sourceExists: Boolean = true,
    var trackingObserverIds: Set<Uuid> = emptySet(),
) : TableStateDeliveryEnvironment {
    override fun locationEntry(tableId: Uuid): TableLocationEntry? = locations.get(tableId)

    override fun hasAuthoritativeState(tableId: Uuid): Boolean = sourceExists

    override fun trackingObservers(entry: TableLocationEntry): Set<Uuid> = trackingObserverIds

    /** 登記一張測試桌並讓其成為目前權威來源。 */
    fun addTable(tableId: Uuid): TableLocationEntry {
        sourceExists = true
        return locations.put(tableId, TableLocation("minecraft:overworld", tableId.hashCode(), 64, 0))
    }

    /** 移除目前桌子位置並標記權威來源已不存在。 */
    fun removeTable(tableId: Uuid) {
        sourceExists = false
        locations.remove(tableId)
    }
}
