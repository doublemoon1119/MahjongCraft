package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 對局事件發布器。
 *
 * `:mahjong-flow` 對外廣播對局內事件的唯一出口——摸牌、捨牌，以及後續的吃碰槓、立直、胡牌、流局，
 * 都透過此介面往外廣播，由外層（如 Minecraft 平台層）決定要如何呈現或轉發。
 * `:mahjong-flow` 本身不知道、也不需要知道外層實際上是誰在監聽。
 *
 * 與 [RoomEventPublisher]（每種事件各一個方法）不同，
 * 這裡直接重用既有的 [GameAction] 密封類別當作事件酬載，避免以後每加一種動作就要在介面上新增方法。
 */
interface GameEventPublisher {
    /**
     * 廣播一則對局內事件。
     *
     * @param gameId 對局 Uuid。
     * @param targetPlayerId 接收此通知的玩家 Uuid。
     * @param actorId 執行該動作的玩家 Uuid。例如 A 摸牌時，通知 A 本人的那則事件 [targetPlayerId] 與此欄位相同，
     *   通知其他玩家「A 摸了牌」的那些事件則不同。[GameAction.GameStarted] 這類系統事件沒有實際執行者，
     *   目前的做法是填入發起開局的房主 Uuid。
     * @param action 發生的動作內容。
     */
    suspend fun publish(gameId: Uuid, targetPlayerId: Uuid, actorId: Uuid, action: GameAction)

    /**
     * 廣播一則對局內事件給整桌——在場玩家＋所有目前已登記的旁觀者（例如透過旁觀機制取得過快照的
     * 非在場玩家）。呼叫端只需要提供在場玩家清單；旁觀者名單完全由實作內部決定，呼叫端不需要知道
     * 旁觀機制存不存在——這樣以後任何用例只要改用這個方法，就不會漏掉旁觀者，不需要每個用例各自
     * 記得去查一次旁觀者名單。
     *
     * @param gameId 對局 Uuid。
     * @param seatedPlayerIds 目前在場（座位上）的玩家 Uuid 清單。
     * @param actorId 執行該動作的玩家 Uuid，語意同 [publish]。
     * @param action 發生的動作內容。
     */
    suspend fun publishToTable(gameId: Uuid, seatedPlayerIds: Collection<Uuid>, actorId: Uuid, action: GameAction)
}
