package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

/**
 * [MahjongPlayerSnapshot] 的網路 DTO。
 *
 * @property componentViews 本次允許交付的具型別規則資料。
 * @property id 玩家 UUID 字串。
 * @property initialSeatIndex 玩家在本局開始時的座位索引。
 * @property seatWind 玩家目前的座位風。
 * @property hand 玩家手牌快照。
 * @property discardPile 玩家牌河。
 * @property tileAreas 玩家移出牌區的已裁切資料。
 * @property score 玩家目前分數。
 */
@Serializable
data class MahjongPlayerSnapshotDto(
    val id: String,
    val initialSeatIndex: Int,
    val seatWind: WindDto,
    val hand: HandSnapshotDto,
    val discardPile: DiscardPileSnapshotDto,
    val tileAreas: List<PlayerTileAreaSnapshotDto>,
    val score: Int,
    val componentViews: List<@Polymorphic ComponentViewDto>,
)
