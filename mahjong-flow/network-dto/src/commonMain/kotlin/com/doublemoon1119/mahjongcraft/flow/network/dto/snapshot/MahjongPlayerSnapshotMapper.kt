package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.NetworkDtoConverter
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import kotlin.uuid.Uuid

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun MahjongPlayerSnapshot.toDto(registries: NetworkDtoConverter): MahjongPlayerSnapshotDto = MahjongPlayerSnapshotDto(
    id = id.toString(),
    componentViews = registries.componentViews.encode(componentViews),
    initialSeatIndex = initialSeatIndex,
    seatWind = seatWind.toDto(),
    hand = hand.toDto(registries),
    discardPile = discardPile.toDto(registries),
    tileAreas = tileAreas.map { it.toDto(registries) },
    score = score,
)

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun MahjongPlayerSnapshotDto.toDomain(registries: NetworkDtoConverter): MahjongPlayerSnapshot = MahjongPlayerSnapshot(
    id = Uuid.parse(id),
    componentViews = registries.componentViews.decode(componentViews),
    initialSeatIndex = initialSeatIndex,
    seatWind = seatWind.toDomain(),
    hand = hand.toDomain(registries),
    discardPile = discardPile.toDomain(registries),
    tileAreas = tileAreas.map { it.toDomain(registries) },
    score = score,
)
