package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry
import kotlin.uuid.Uuid

/**
 * 提供桌級交付使用的目前來源身分；唯讀查詢不會建立或修改權威狀態。
 *
 * session 與位置條目查詢必須讀取已發布的不可變資料；追蹤觀察者只在伺服器主執行緒查詢。
 */
interface TableStateDeliveryEnvironment {
    /** 目前伺服器這次啟動的身分；未啟動時為 null。 */
    val sessionId: Uuid?

    /**
     * 取得目前桌子位置條目，物件身分代表這次位置來源。
     *
     * @param tableId 要查詢的桌子。
     * @return 目前位置條目；已移除時為 null。
     */
    fun locationEntry(tableId: Uuid): TableLocationEntry?

    /**
     * 判斷該桌是否仍有權威房間或對局，不等待交易鎖。
     *
     * @param tableId 要查詢的桌子。
     * @return 還有有效來源時為 true。
     */
    fun hasAuthoritativeState(tableId: Uuid): Boolean

    /**
     * 在主執行緒取得正在追蹤原位置的玩家，不使用移除後的索引查詢。
     *
     * @param entry 清理前捕捉的位置條目。
     * @return 目前追蹤該位置的玩家快照。
     */
    fun trackingObservers(entry: TableLocationEntry): Set<Uuid>
}
