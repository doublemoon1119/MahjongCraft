package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DiscardPileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.PlayerRuleStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** [MahjongPlayerSnapshot] 的網路 DTO。 */
@Serializable
data class MahjongPlayerSnapshotDto(
    val id: String,
    val initialSeat: WindDto,
    val currentWind: WindDto,
    val hand: HandSnapshotDto,
    val discardPile: DiscardPileDto,
    val playerRuleState: PlayerRuleStateDto?,
    val score: Int,
    val isAi: Boolean,
)

fun MahjongPlayerSnapshot.toDto(registries: NetworkDtoRegistries): MahjongPlayerSnapshotDto = MahjongPlayerSnapshotDto(
    id = id.toString(),
    initialSeat = initialSeat.toDto(),
    currentWind = currentWind.toDto(),
    hand = hand.toDto(),
    discardPile = discardPile.toDto(registries),
    playerRuleState = playerRuleState?.toDto(registries),
    score = score,
    isAi = isAi,
)

fun MahjongPlayerSnapshotDto.toDomain(registries: NetworkDtoRegistries): MahjongPlayerSnapshot = MahjongPlayerSnapshot(
    id = Uuid.parse(id),
    initialSeat = initialSeat.toDomain(),
    currentWind = currentWind.toDomain(),
    hand = hand.toDomain(),
    discardPile = discardPile.toDomain(registries),
    playerRuleState = playerRuleState?.toDomain(registries),
    score = score,
    isAi = isAi,
)
