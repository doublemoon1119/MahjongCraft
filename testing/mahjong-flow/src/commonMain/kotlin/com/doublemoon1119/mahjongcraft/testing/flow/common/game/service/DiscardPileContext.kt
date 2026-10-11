package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import kotlin.uuid.Uuid

/**
 * [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishDiscardPileUpdated] 資料。
 *
 * @property seatIndex 牌河所屬玩家座位索引。
 * @property discardTileIds 牌河中的牌 UUID。
 * @property sidewaysMarkedTileId 橫置標記牌 UUID，沒有標記時為 null。
 * @property newlyDiscardedTileId 本次新增捨牌 UUID，沒有新增時為 null。
 */
data class DiscardPileContext(
    val seatIndex: Int,
    val discardTileIds: List<Uuid>,
    val sidewaysMarkedTileId: Uuid?,
    val newlyDiscardedTileId: Uuid?,
)
