package com.doublemoon1119.mahjongcraft.flow.server.membership.repository

import kotlin.uuid.Uuid

/** 伺服器內玩家與麻將桌唯一歸屬關係的權威存取介面。 */
interface PlayerMembershipRepository {
    /**
     * 嘗試讓玩家原子占用指定麻將桌。
     *
     * @return 成功占用或原本已占用同桌時為 true；已占用其他桌時為 false。
     */
    suspend fun claim(playerId: Uuid, tableId: Uuid): Boolean

    /** 取得玩家目前占用的麻將桌識別碼；尚未參與遊戲時為 null。 */
    suspend fun getTableId(playerId: Uuid): Uuid?

    /** 僅在玩家仍占用指定桌時釋放歸屬，避免舊流程誤刪新的歸屬。 */
    suspend fun release(playerId: Uuid, tableId: Uuid)

    /** 以已驗證的完整內容取代目前 server session 的所有玩家歸屬。 */
    suspend fun replaceAll(tableIdsByPlayerId: Map<Uuid, Uuid>)

    /** 清除目前 server session 的所有玩家歸屬。 */
    suspend fun clearAll()
}
