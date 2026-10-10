package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.RoomSnapshotDto
import kotlinx.serialization.Serializable

/**
 * 一張麻將桌目前的佔用狀態與公開資訊。
 *
 * [roomSnapshot] 只在 [TableOccupancyDto.ROOM] 時附帶；`playing` 開頭的欄位只在 [TableOccupancyDto.GAME]
 * 時有值。座標供客戶端判斷玩家是否仍在桌子附近。
 *
 * @property tableId 麻將桌 ID。
 * @property occupancy 桌子的佔用狀態。
 * @property roomSnapshot 等待中的房間快照。
 * @property playingPlayerIds 進行中玩家 UUID 字串。
 * @property playingAiPlayerIds 進行中 AI 玩家 UUID 字串。
 * @property playingGameConfig 進行中的遊戲設定。
 * @property dimensionId 遊戲維度 ID。
 * @property tableX 麻將桌 X 座標。
 * @property tableY 麻將桌 Y 座標。
 * @property tableZ 麻將桌 Z 座標。
 */
@Serializable
data class TableOccupancyPayloadDto(
    val tableId: String,
    val occupancy: TableOccupancyDto,
    val roomSnapshot: RoomSnapshotDto?,
    val playingPlayerIds: List<String>,
    val playingAiPlayerIds: List<String>,
    val playingGameConfig: GameConfigDto?,
    val dimensionId: String?,
    val tableX: Int?,
    val tableY: Int?,
    val tableZ: Int?,
)
