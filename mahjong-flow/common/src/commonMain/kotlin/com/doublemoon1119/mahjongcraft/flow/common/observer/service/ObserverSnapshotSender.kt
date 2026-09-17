package com.doublemoon1119.mahjongcraft.flow.common.observer.service

import com.doublemoon1119.mahjongcraft.flow.common.observer.model.ObserverSnapshot
import kotlin.uuid.Uuid

/**
 * 把一份內容送給單一觀察者。
 *
 * 由平台層實作。這裡只負責資料同步，不攜帶任何動作語意，也不產生文字訊息——帶事件的通知仍由
 * [com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher] 與
 * [com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher] 負責。
 */
interface ObserverSnapshotSender {
    /**
     * 送出指定觀察者的內容。
     *
     * @param id 房間或對局的 Uuid。
     * @param observerId 觀察者的 Uuid。
     * @param snapshot 該觀察者目前應該看到的內容；房間與對局都不存在時為 `null`，代表要清除他手上的
     *   既有內容。
     */
    suspend fun send(id: Uuid, observerId: Uuid, snapshot: ObserverSnapshot?)
}
