package com.doublemoon1119.mahjongcraft.flow.common.game.service

import kotlin.uuid.Uuid

/**
 * 一位玩家目前的牌區，內容與 [GamePresentationPublisher.publishPlayerTilesUpdated] 的同名參數相同。
 *
 * @property seatIndex 這位玩家在 `TableState.players` 的固定座位 index。
 * @property standingTileIds 立牌，依手牌順序排列，不含 [drawnTileId]。
 * @property drawnTileId 摸牌位上的牌；沒有時為 null。
 * @property melds 副露，依宣告順序排列。
 * @property setAsideTileIds 移出手牌、公開擺在桌上的牌，依移出順序排列。
 */
data class PlayerAreaPresentationState(
    val seatIndex: Int,
    val standingTileIds: List<Uuid>,
    val drawnTileId: Uuid?,
    val melds: List<MeldPresentation>,
    val setAsideTileIds: List<Uuid>,
)
