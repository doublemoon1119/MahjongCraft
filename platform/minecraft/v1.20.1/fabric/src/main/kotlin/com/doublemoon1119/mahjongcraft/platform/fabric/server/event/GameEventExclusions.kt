package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/** 共用成就判定與事件掛勾的 debug 場次排除名單；可由提交執行緒安全查詢。 */
@Single
class GameEventExclusions {
    /** 此次伺服器 session 中不再通知的場次。 */
    private val matchIds: MutableSet<Uuid> = ConcurrentHashMap.newKeySet()

    /**
     * 排除指定場次後續的成就與事件。
     * @param matchId 使用 debug 操作的場次。
     */
    fun exclude(matchId: Uuid) {
        matchIds += matchId
    }

    /**
     * 查詢場次是否已被排除。
     * @param matchId 欲查詢的場次。
     * @return 場次已被排除時為 true。
     */
    fun contains(matchId: Uuid): Boolean = matchId in matchIds

    /** 結束 session 時清除名單，不讓排除狀態帶入另一個世界。 */
    fun clear() {
        matchIds.clear()
    }
}
