package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 供測試使用的 [GameEventPublisher] 模擬實作。
 *
 * 紀錄所有發送出的對局事件通知，以便在單元測試中驗證業務邏輯是否正確向特定玩家發送了特定動作。
 */
class FakeGameEventPublisher : GameEventPublisher {
    /**
     * 紀錄發送過的事件。
     *
     * Key: Triple(對局ID, 接收者ID, 執行者ID)
     */
    private val notifications = mutableMapOf<Triple<Uuid, Uuid, Uuid>, GameAction>()

    override suspend fun publish(gameId: Uuid, targetPlayerId: Uuid, actorId: Uuid, action: GameAction) {
        notifications[Triple(gameId, targetPlayerId, actorId)] = action
    }

    /**
     * 獲取特定玩家收到的事件動作內容。
     *
     * @param gameId 對局 Uuid。
     * @param targetPlayerId 接收通知的玩家 Uuid。
     * @param actorId 發生事件的執行者 Uuid。
     * @return 該事件的 [GameAction]，若無紀錄則回傳 null。
     */
    fun getNotifiedAction(
        gameId: Uuid,
        targetPlayerId: Uuid,
        actorId: Uuid
    ): GameAction? = notifications[Triple(gameId, targetPlayerId, actorId)]
}
