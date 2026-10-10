package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import kotlin.uuid.Uuid

/**
 * 將領域資料轉換為網路 DTO。
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun MahjongPlayerSnapshot.toDto(registries: NetworkDtoRegistries): MahjongPlayerSnapshotDto = MahjongPlayerSnapshotDto(
    id = id.toString(),
    initialSeatIndex = initialSeatIndex,
    seatWind = seatWind.toDto(),
    hand = hand.toDto(),
    discardPile = discardPile.toDto(registries),
    setAsideTiles = setAsideTiles.map { it.toDto() },
    playerRuleState = playerRuleState?.toDto(registries),
    score = score,
)

/**
 * 將網路 DTO 還原為領域資料。
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun MahjongPlayerSnapshotDto.toDomain(registries: NetworkDtoRegistries): MahjongPlayerSnapshot = MahjongPlayerSnapshot(
    id = Uuid.parse(id),
    initialSeatIndex = initialSeatIndex,
    seatWind = seatWind.toDomain(),
    hand = hand.toDomain(),
    discardPile = discardPile.toDomain(registries),
    setAsideTiles = setAsideTiles.map { it.toDomain() },
    playerRuleState = playerRuleState?.toDomain(registries),
    score = score,
)
