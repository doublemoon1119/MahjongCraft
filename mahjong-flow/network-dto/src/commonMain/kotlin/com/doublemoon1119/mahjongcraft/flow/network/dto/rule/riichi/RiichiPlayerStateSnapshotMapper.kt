package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerStateSnapshot

/** 將已按觀察者裁切的日麻玩家狀態轉為網路 DTO，不改變欄位可見範圍。 */
fun RiichiPlayerStateSnapshot.toRiichiSnapshotDto(): RiichiPlayerStateSnapshotDto = RiichiPlayerStateSnapshotDto(
    riichiTile = riichiTile?.toDto(),
    doubleRiichiTile = doubleRiichiTile?.toDto(),
    isIppatsu = isIppatsu,
    paoLiability = paoLiability?.toDto(),
    isPermanentlyFuriten = isPermanentlyFuriten,
    nukiDoraTiles = nukiDoraTiles.map { it.toDto() },
)

/** 將網路 DTO 還原為日麻玩家狀態快照；此快照不得回寫權威狀態。 */
fun RiichiPlayerStateSnapshotDto.toDomain(): RiichiPlayerStateSnapshot = RiichiPlayerStateSnapshot(
    riichiTile = riichiTile?.toDomain(),
    doubleRiichiTile = doubleRiichiTile?.toDomain(),
    isIppatsu = isIppatsu,
    paoLiability = paoLiability?.toDomain(),
    isPermanentlyFuriten = isPermanentlyFuriten,
    nukiDoraTiles = nukiDoraTiles.map { it.toDomain() },
)
