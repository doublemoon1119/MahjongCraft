package com.doublemoon1119.mahjongcraft.flow.network.dto

import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** [TableStateSnapshot] 的 observer-specific 網路 DTO。 */
@Serializable
data class TableStateSnapshotDto(
    val id: String,
    val players: List<MahjongPlayerSnapshotDto>,
    val config: MahjongRuleConfigDto,
    val tileWall: TileWallSnapshotDto,
    val prevalentWind: WindDto,
    val roundNumber: Int,
    val comboCount: Int,
    val currentPlayerIndex: Int,
    val dynamicRuleState: DynamicRuleStateDto?,
)

fun TableStateSnapshot.toDto(): TableStateSnapshotDto = TableStateSnapshotDto(
    id = id.toString(),
    players = players.map { it.toDto() },
    config = config.toDto(),
    tileWall = tileWall.toDto(),
    prevalentWind = prevalentWind.toDto(),
    roundNumber = roundNumber,
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    dynamicRuleState = dynamicRuleState?.toDto(),
)

fun TableStateSnapshotDto.toDomain(): TableStateSnapshot = TableStateSnapshot(
    id = Uuid.parse(id),
    players = players.map { it.toDomain() },
    config = config.toDomain(),
    tileWall = tileWall.toDomain(),
    prevalentWind = prevalentWind.toDomain(),
    roundNumber = roundNumber,
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    dynamicRuleState = dynamicRuleState?.toDomain(),
)
