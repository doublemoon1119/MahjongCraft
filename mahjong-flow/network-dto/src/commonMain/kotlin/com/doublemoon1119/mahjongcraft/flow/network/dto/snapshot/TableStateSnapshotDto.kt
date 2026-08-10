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
    val prevalentWind: WindDto,
    val roundNumber: Int,
    val comboCount: Int,
    val currentPlayerIndex: Int,
    val dynamicRuleState: DynamicRuleStateDto?,
)

fun TableStateSnapshot.toDto(registries: NetworkDtoRegistries): TableStateSnapshotDto = TableStateSnapshotDto(
    id = id.toString(),
    players = players.map { it.toDto(registries) },
    config = config.toDto(registries),
    tileWall = tileWall.toDto(),
    prevalentWind = prevalentWind.toDto(),
    roundNumber = roundNumber,
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    dynamicRuleState = dynamicRuleState?.toDto(registries),
)

fun TableStateSnapshotDto.toDomain(registries: NetworkDtoRegistries): TableStateSnapshot = TableStateSnapshot(
    id = Uuid.parse(id),
    players = players.map { it.toDomain(registries) },
    config = config.toDomain(registries),
    tileWall = tileWall.toDomain(),
    prevalentWind = prevalentWind.toDomain(),
    roundNumber = roundNumber,
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    dynamicRuleState = dynamicRuleState?.toDomain(registries),
)
