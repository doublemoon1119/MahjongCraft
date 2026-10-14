package com.doublemoon1119.mahjongcraft.flow.server.membership.repository

import kotlin.uuid.Uuid

/** 伺服器內玩家與場地唯一歸屬關係的權威存取介面。 */
interface PlayerMembershipRepository {
    /**
     * 嘗試讓玩家原子占用指定場地。
     *
     * @return 成功占用或原本已占用同一場地時為 true；已占用其他場地時為 false。
     */
    suspend fun claim(playerId: Uuid, venueId: Uuid): Boolean

    /** 取得玩家目前占用的場地識別碼；尚未參與遊戲時為 null。 */
    suspend fun getVenueId(playerId: Uuid): Uuid?

    /** 僅在玩家仍占用指定桌時釋放歸屬，避免舊流程誤刪新的歸屬。 */
    suspend fun release(playerId: Uuid, venueId: Uuid)

    /**
     * 在 repository 互斥鎖內確認來源仍有效後釋放玩家歸屬。
     *
     * 條件只能讀取已發布資料，不得阻塞、重新呼叫此 repository 或修改來源。
     *
     * @param playerId 玩家識別碼。
     * @param venueId 要釋放的場地識別碼。
     * @param isStillValid 鎖內刪除前確認來源仍屬於這次工作的條件。
     * @return 條件成立並完成這次釋放時為 true；條件不成立時不修改資料並回傳 false。
     */
    suspend fun releaseIf(
        playerId: Uuid,
        venueId: Uuid,
        isStillValid: () -> Boolean,
    ): Boolean

    /** 以已驗證的完整內容取代目前 server session 的所有玩家歸屬。 */
    suspend fun replaceAll(venueIdsByPlayerId: Map<Uuid, Uuid>)

    /** 清除目前 server session 的所有玩家歸屬。 */
    suspend fun clearAll()
}
