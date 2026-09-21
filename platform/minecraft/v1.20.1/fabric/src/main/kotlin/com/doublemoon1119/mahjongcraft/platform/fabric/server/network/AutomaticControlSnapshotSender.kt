package com.doublemoon1119.mahjongcraft.platform.fabric.server.network

import kotlin.uuid.Uuid

/** 將單一玩家的本局自動操作權威快照送往其目前連線；玩家不在線時安全略過。 */
interface AutomaticControlSnapshotSender {
    /** 依目前權威對局建立並送出 [playerId] 自己的快照；不再是成員時改送清除訊號。 */
    suspend fun send(gameId: Uuid, playerId: Uuid)

    /** 清除 [playerId] 客戶端保存的本局自動操作狀態。 */
    fun clear(playerId: Uuid)
}
