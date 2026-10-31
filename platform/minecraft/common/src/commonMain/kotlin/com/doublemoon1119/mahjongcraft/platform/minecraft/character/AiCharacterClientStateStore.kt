package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlin.uuid.Uuid

/** 客戶端角色資料；由登入生命週期指定 session，收到的更新不能自行切換 session。 */
class AiCharacterClientStateStore {
    /** 目前已確認的伺服器 session。 */
    private var sessionId: Uuid? = null

    /** 每張桌子最近接受的公開快照。 */
    private val tables = mutableMapOf<Uuid, AiCharacterSyncSnapshot>()

    /**
     * 以已確認的登入資料開始 session，清除前一世界的內容。
     *
     * @param id 這次伺服器 session。
     */
    fun beginSession(id: Uuid) {
        sessionId = id
        tables.clear()
    }

    /**
     * 接受同一 session 的較新版本；舊封包不能覆寫已接受內容。
     *
     * @param snapshot 完整公開角色快照。
     * @return 此快照被接受時為 true。
     */
    fun apply(snapshot: AiCharacterSyncSnapshot): Boolean {
        if (snapshot.sessionId != sessionId) return false
        val previous = tables[snapshot.tableId]
        if (previous != null && snapshot.revision <= previous.revision) return false
        tables[snapshot.tableId] = snapshot
        return true
    }

    /**
     * 查詢最後接受的桌子角色資料。
     *
     * @param tableId 桌子 UUID。
     * @return 同一 session 的公開資料；尚未同步時為 null。
     */
    fun find(tableId: Uuid): AiCharacterSyncSnapshot? = tables[tableId]

    /** 斷線時清除所有資料；延後抵達的舊更新會被拒絕。 */
    fun clear() {
        sessionId = null
        tables.clear()
    }
}
