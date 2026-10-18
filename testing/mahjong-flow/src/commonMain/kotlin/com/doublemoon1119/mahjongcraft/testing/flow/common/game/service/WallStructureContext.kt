package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import kotlin.uuid.Uuid

/**
 * [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishWallStructure] 隨附桌況資料。
 *
 * @property dealerSeatIndex 莊家座位索引。
 * @property deadWallTileIds 王牌區牌的 UUID 集合。
 * @property openingDiceCounts 各輪擲骰使用的骰子數量。
 * @property isNewOpening 是否為新的開局。
 * @property revealedTileIds 已公開的牌 UUID 集合。
 */
data class WallStructureContext(
    val dealerSeatIndex: Int,
    val deadWallTileIds: Set<Uuid>,
    val openingDiceCounts: List<Int>,
    val isNewOpening: Boolean,
    val revealedTileIds: Set<Uuid>,
)
