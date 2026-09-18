package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.RoomSnapshotDto
import kotlinx.serialization.Serializable

/** 一張麻將桌目前被什麼佔用。 */
@Serializable
enum class TableOccupancyDto {
    /** 房間與對局都不存在。 */
    VACANT,

    /** 等待階段的房間。 */
    ROOM,

    /** 進行中的對局。 */
    GAME,
}

/**
 * 一張麻將桌目前的佔用狀態與公開資訊。
 *
 * [roomSnapshot] 只在 [TableOccupancyDto.ROOM] 時附帶；`playing` 開頭的欄位只在 [TableOccupancyDto.GAME]
 * 時有值。座標供客戶端判斷玩家是否仍在桌子附近。
 */
@Serializable
data class TableOccupancyPayloadDto(
    val tableId: String,
    val occupancy: TableOccupancyDto,
    val roomSnapshot: RoomSnapshotDto? = null,
    val playingPlayerIds: List<String> = emptyList(),
    val playingAiPlayerIds: List<String> = emptyList(),
    val playingGameConfig: GameConfigDto? = null,
    val dimensionId: String? = null,
    val tableX: Int? = null,
    val tableY: Int? = null,
    val tableZ: Int? = null,
)
