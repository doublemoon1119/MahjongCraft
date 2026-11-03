package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlin.uuid.Uuid

/** 客戶端角色資料；由登入生命週期指定 session，收到的更新不能自行切換 session。 */
class AiCharacterClientStateStore {
    /** 目前已確認的伺服器 session。 */
    private var sessionId: Uuid? = null

    /** 每張桌子最近接受的公開快照。 */
    private val tables = mutableMapOf<Uuid, AiCharacterSyncSnapshot>()

    /** 成員對桌子的反向索引，避免每個頭像每幀搜尋所有已知桌子。 */
    private val byPlayer = mutableMapOf<Uuid, Pair<Uuid, AiCharacterSnapshot>>()

    /** 已授權歷史的固定外觀，與目前世界的角色完全分開。 */
    private val historical = linkedMapOf<Uuid, Map<Uuid, AiCharacterSnapshot>>()

    /**
     * 保存已與摘要要求配對的當場外觀，不覆寫目前對局。
     *
     * @param matchId 歷史場次。
     * @param players 該場已授權的完整描述。
     */
    fun rememberHistory(matchId: Uuid, players: Map<Uuid, AiCharacterSnapshot>) {
        require(players.size <= AiCharacterSyncPayload.MAX_PLAYERS) { "Too many historical character players" }
        if (historical.size >= 64 && matchId !in historical) historical.remove(historical.keys.first())
        historical[matchId] = players.toMap()
    }

    /**
     * 只查指定場次的固定外觀，缺少時不拿目前世界的造型代替。
     *
     * @param matchId 已授權歷史場次。
     * @param playerId 該場成員。
     * @return 當場描述，沒有時為 null。
     */
    fun history(matchId: Uuid, playerId: Uuid): AiCharacterSnapshot? = historical[matchId]?.get(playerId)

    /**
     * 以已確認的登入資料開始 session，清除前一世界的內容。
     *
     * @param id 這次伺服器 session。
     */
    fun beginSession(id: Uuid) {
        sessionId = id
        tables.clear()
        byPlayer.clear()
        historical.clear()
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
        previous?.players?.keys?.forEach { player -> if (byPlayer[player]?.first == snapshot.tableId) byPlayer.remove(player) }
        tables[snapshot.tableId] = snapshot
        snapshot.players.forEach { (player, appearance) -> byPlayer[player] = snapshot.tableId to appearance }
        return true
    }

    /**
     * 查詢最後接受的桌子角色資料。
     *
     * @param tableId 桌子 UUID。
     * @return 同一 session 的公開資料；尚未同步時為 null。
     */
    fun find(tableId: Uuid): AiCharacterSyncSnapshot? = tables[tableId]

    /**
     * 查詢目前仍可見名冊中的角色，不查詢世界實體。
     *
     * @param playerId 成員編號。
     * @return 目前公開描述，沒有時為 null。
     */
    fun findPlayer(playerId: Uuid): AiCharacterSnapshot? = byPlayer[playerId]?.second

    /**
     * 清除已失去觀察權或被移除的桌子資料。
     *
     * @param tableId 已清除的桌子。
     */
    fun remove(tableId: Uuid) {
        tables.remove(tableId)?.players?.keys?.forEach { player -> if (byPlayer[player]?.first == tableId) byPlayer.remove(player) }
    }

    /**
     * 接受這個連線的初始 session，之後不允許封包任意切換世界。
     *
     * @param snapshot 由已檢查連線世代的接收器交付的資料。
     * @return 是否成功接受。
     */
    fun receive(snapshot: AiCharacterSyncSnapshot): Boolean {
        if (sessionId == null) beginSession(snapshot.sessionId)
        return apply(snapshot)
    }

    /** 斷線時清除所有資料；延後抵達的舊更新會被拒絕。 */
    fun clear() {
        sessionId = null
        tables.clear()
        byPlayer.clear()
        historical.clear()
    }
}
