package com.doublemoon1119.mahjongcraft.flow.network.dto.message
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerAutomaticControlSnapshot
import kotlin.uuid.Uuid

/**
 * 將領域資料轉換為網路 DTO。
 * @return 轉換後的資料。
 */
fun PlayerAutomaticControlSnapshot.toDto(): AutomaticControlSnapshotDto = AutomaticControlSnapshotDto(
    gameId = gameId.toString(),
    revision = revision,
    supportedControlIds = supportedControlIds,
    enabledControlIds = enabledControlIds,
)

/**
 * 將網路快照還原成個人權威狀態。
 *
 * @return 轉換後的資料。
 */
fun AutomaticControlSnapshotDto.toDomain(): PlayerAutomaticControlSnapshot = PlayerAutomaticControlSnapshot(
    gameId = Uuid.parse(gameId),
    revision = revision,
    supportedControlIds = supportedControlIds,
    enabledControlIds = enabledControlIds,
)
