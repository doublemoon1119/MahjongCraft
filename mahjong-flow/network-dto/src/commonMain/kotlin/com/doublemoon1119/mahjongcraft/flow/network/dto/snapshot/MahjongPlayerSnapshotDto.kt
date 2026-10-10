package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DiscardPileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.PlayerRuleStateDto
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import kotlinx.serialization.Serializable

/**
 * [MahjongPlayerSnapshot] 的網路 DTO。
 *
 * @property id 玩家 UUID 字串。
 * @property initialSeatIndex 玩家在本局開始時的座位索引。
 * @property seatWind 玩家目前的座位風。
 * @property hand 玩家手牌快照。
 * @property discardPile 玩家牌河。
 * @property setAsideTiles 玩家保留在手牌外的牌張。
 * @property playerRuleState 規則專屬玩家狀態；沒有時為 `null`。
 * @property score 玩家目前分數。
 */
@Serializable
data class MahjongPlayerSnapshotDto(
    val id: String,
    val initialSeatIndex: Int,
    val seatWind: WindDto,
    val hand: HandSnapshotDto,
    val discardPile: DiscardPileDto,
    val setAsideTiles: List<IdentifiedTileDto>,
    val playerRuleState: PlayerRuleStateDto?,
    val score: Int,
)
