package com.doublemoon1119.mahjongcraft.flow.dto

import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class MahjongPlayerSnapshotDto(
    val id: String,
    val initialSeat: WindDto,
    val hand: HandSnapshotDto,
    val discardPile: DiscardPileDto,
    val playerRuleState: PlayerRuleStateDto?,
    val score: Int,
    val isAi: Boolean,
)

fun MahjongPlayerSnapshot.toDto(): MahjongPlayerSnapshotDto = MahjongPlayerSnapshotDto(
    id = id.toString(),
    initialSeat = initialSeat.toDto(),
    hand = hand.toDto(),
    discardPile = discardPile.toDto(),
    playerRuleState = playerRuleState?.toDto(),
    score = score,
    isAi = isAi,
)

fun MahjongPlayerSnapshotDto.toDomain(): MahjongPlayerSnapshot = MahjongPlayerSnapshot(
    id = Uuid.parse(id),
    initialSeat = initialSeat.toDomain(),
    hand = hand.toDomain(),
    discardPile = discardPile.toDomain(),
    playerRuleState = playerRuleState?.toDomain(),
    score = score,
    isAi = isAi,
)
