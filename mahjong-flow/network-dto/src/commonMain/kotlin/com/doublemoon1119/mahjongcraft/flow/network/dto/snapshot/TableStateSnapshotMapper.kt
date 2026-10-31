package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.NetworkDtoConverter
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun TableStateSnapshot.toDto(registries: NetworkDtoConverter): TableStateSnapshotDto = TableStateSnapshotDto(
    id = id.toString(),
    componentViews = registries.componentViews.encode(componentViews),
    players = players.map { it.toDto(registries) },
    config = config.toDto(registries),
    tileWall = tileWall.toDto(registries),
    dealerPlayerId = dealerPlayerId.toString(),
    prevalentWind = prevalentWind.toDto(),
    roundNumber = roundNumber,
    roundPosition = roundPosition.toDto(),
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    finishedPlayerIds = finishedPlayerIds.map(Uuid::toString).toSet(),
    physicalWallLayout = physicalWallLayout?.toDto(),
    openingDiceRolls = openingDiceRolls.map { it.toDto() },
    publicTileIds = publicTileIds.map(Uuid::toString).toSet(),
    publicEvidenceTileIds = publicEvidenceTileIds.map(Uuid::toString).toSet(),
    tilePoseStates = tilePoseStates.map { it.toDto() },
)

/**
 * 將網路 DTO 還原成 observer-specific 桌況快照。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun TableStateSnapshotDto.toDomain(registries: NetworkDtoConverter): TableStateSnapshot = TableStateSnapshot(
    id = Uuid.parse(id),
    componentViews = registries.componentViews.decode(componentViews),
    players = players.map { it.toDomain(registries) },
    config = config.toDomain(registries),
    tileWall = tileWall.toDomain(registries),
    dealerPlayerId = Uuid.parse(dealerPlayerId),
    prevalentWind = prevalentWind.toDomain(),
    roundNumber = roundNumber,
    roundPosition = roundPosition.toDomain(),
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    finishedPlayerIds = finishedPlayerIds.map(Uuid::parse).toSet(),
    physicalWallLayout = physicalWallLayout?.toDomain(),
    openingDiceRolls = openingDiceRolls.map { it.toDomain() },
    publicTileIds = publicTileIds.map(Uuid::parse).toSet(),
    publicEvidenceTileIds = publicEvidenceTileIds.map(Uuid::parse).toSet(),
    tilePoseStates = tilePoseStates.map { it.toDomain() },
)
