package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

/**
 * 單一玩家的流局結算資料。
 *
 * @property ranking 這名玩家結算前後的分數與名次。
 * @property seatWind 結算當下風位。
 * @property handTileIds 這名玩家完整立牌的 Uuid，不包含副露或牌河。
 * @property handDisclosure 規則要求如何公開這副手牌。
 * @property revealedHandTileIds 規則要求公開的完整手牌 Uuid；空集合代表不推牌。
 * @property waitingTiles 規則已計算完成的等待牌；空集合代表不顯示等待牌。
 * @property statusId 玩家狀態的 namespaced ID；不需要額外狀態時為 null。
 */
data class ExhaustiveDrawSettlementPlayerPresentation(
    val ranking: ScoreRankingPlayer,
    val seatWind: Wind,
    val handTileIds: List<Uuid>,
    val handDisclosure: ExhaustiveDrawHandDisclosure,
    val revealedHandTileIds: List<Uuid>,
    val waitingTiles: List<Tile>,
    val statusId: String?,
)
