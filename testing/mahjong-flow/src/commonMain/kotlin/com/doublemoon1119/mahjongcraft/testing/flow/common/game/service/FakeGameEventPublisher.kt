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
     * 依發送順序紀錄的事件列表。
     *
     * Key: Triple(對局ID, 接收者ID, 執行者ID)
     */
    private val notifications = mutableMapOf<Triple<Uuid, Uuid, Uuid>, MutableList<GameAction>>()

    override suspend fun publish(gameId: Uuid, targetPlayerId: Uuid, actorId: Uuid, action: GameAction) {
        notifications.getOrPut(Triple(gameId, targetPlayerId, actorId)) { mutableListOf() }.add(action)
    }

    /**
     * 獲取特定玩家收到的「最後一次」事件動作內容。
     *
     * 若同一組 (gameId, targetPlayerId, actorId) 曾收到多次通知（例如立直宣告會依序廣播
     * [GameAction.Riichi] 再廣播 [GameAction.Discard]），只會回傳最後一次；需要驗證完整順序時
     * 請改用 [getNotifiedActions]。
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
    ): GameAction? = notifications[Triple(gameId, targetPlayerId, actorId)]?.lastOrNull()

    /**
     * 獲取特定玩家依發送順序收到的所有事件動作內容。
     *
     * @param gameId 對局 Uuid。
     * @param targetPlayerId 接收通知的玩家 Uuid。
     * @param actorId 發生事件的執行者 Uuid。
     * @return 依發送順序排列的 [GameAction] 列表，若無紀錄則為空列表。
     */
    fun getNotifiedActions(
        gameId: Uuid,
        targetPlayerId: Uuid,
        actorId: Uuid
    ): List<GameAction> = notifications[Triple(gameId, targetPlayerId, actorId)].orEmpty()
}
