package com.doublemoon1119.mahjongcraft.flow.server.game.service

import kotlin.uuid.Uuid

/** 回答玩家目前是否在線，由平台依伺服器上的連線狀態實作。 */
fun interface PlayerPresence {
    /**
     * [playerId] 目前是否在線。
     *
     * @param playerId 欲查詢的玩家。
     * @return 玩家在線時為 true。
     */
    fun isOnline(playerId: Uuid): Boolean

    /** 不依連線狀態的預設值。 */
    companion object {
        /** 把每位玩家都當成在線，用於沒有連線概念的環境，例如離線產生牌譜與測試。 */
        val ALWAYS_ONLINE: PlayerPresence = PlayerPresence { true }
    }
}
