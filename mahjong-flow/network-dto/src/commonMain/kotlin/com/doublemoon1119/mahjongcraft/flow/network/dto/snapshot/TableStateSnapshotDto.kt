package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DynamicRuleStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto
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
    val dealerPlayerId: String,
    val prevalentWind: WindDto,
    val roundNumber: Int,
    val roundPosition: MatchRoundPositionDto,
    val comboCount: Int,
    val currentPlayerIndex: Int,
    val dynamicRuleState: DynamicRuleStateDto?,
    val finishedPlayerIds: Set<String>,
)

/** 將 observer-specific 桌況快照轉成網路 DTO。 */
fun TableStateSnapshot.toDto(registries: NetworkDtoRegistries): TableStateSnapshotDto = TableStateSnapshotDto(
    id = id.toString(),
    players = players.map { it.toDto(registries) },
    config = config.toDto(registries),
    tileWall = tileWall.toDto(),
    dealerPlayerId = dealerPlayerId.toString(),
    prevalentWind = prevalentWind.toDto(),
    roundNumber = roundNumber,
    roundPosition = roundPosition.toDto(),
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    dynamicRuleState = dynamicRuleState?.toDto(registries),
    finishedPlayerIds = finishedPlayerIds.map(Uuid::toString).toSet(),
)

/** 將網路 DTO 還原成 observer-specific 桌況快照。 */
fun TableStateSnapshotDto.toDomain(registries: NetworkDtoRegistries): TableStateSnapshot = TableStateSnapshot(
    id = Uuid.parse(id),
    players = players.map { it.toDomain(registries) },
    config = config.toDomain(registries),
    tileWall = tileWall.toDomain(),
    dealerPlayerId = Uuid.parse(dealerPlayerId),
    prevalentWind = prevalentWind.toDomain(),
    roundNumber = roundNumber,
    roundPosition = roundPosition.toDomain(),
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    dynamicRuleState = dynamicRuleState?.toDomain(registries),
    finishedPlayerIds = finishedPlayerIds.map(Uuid::parse).toSet(),
)
