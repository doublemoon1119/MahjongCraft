package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import kotlin.uuid.Uuid

/**
 * [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishInitialDeal] 資料。
 *
 * @property handTileIdsBySeatIndex 各座位初始手牌的牌 UUID。
 * @property postFlipHandTileIdsBySeatIndex 各座位翻牌後手牌的牌 UUID。
 * @property dealerSeatIndex 莊家座位索引。
 * @property dealBatchSizes 各次發牌的張數。
 * @property openingDiceCounts 各輪擲骰使用的骰子數量。
 */
data class InitialDealContext(
    val handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
    val postFlipHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
    val dealerSeatIndex: Int,
    val dealBatchSizes: List<Int>,
    val openingDiceCounts: List<Int>,
)
