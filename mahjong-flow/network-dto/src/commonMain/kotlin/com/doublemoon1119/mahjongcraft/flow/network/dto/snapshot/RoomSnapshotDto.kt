package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import kotlinx.serialization.Serializable

/**
 * [RoomSnapshot] 的 observer-specific 網路 DTO。
 *
 * @property id 房間 UUID 字串。
 * @property hostId 房主 UUID 字串。
 * @property gameConfig 房間使用的遊戲設定。
 * @property playerIds 房間內玩家 UUID 字串。
 * @property readyPlayerIds 已準備玩家 UUID 字串。
 * @property aiPlayerIds 由 AI 操控的玩家 UUID 字串。
 * @property aiPlayerStrategyKeys 各 AI 玩家使用的策略識別碼。
 * @property canStart 房間目前是否可開始對局。
 * @property isHost 目前 observer 是否為房主。
 * @property isInRoom 目前 observer 是否位於房間中。
 */
@Serializable
data class RoomSnapshotDto(
    val id: String,
    val hostId: String,
    val gameConfig: GameConfigDto,
    val playerIds: List<String>,
    val readyPlayerIds: List<String>,
    val aiPlayerIds: List<String>,
    val aiPlayerStrategyKeys: Map<String, String>,
    val canStart: Boolean,
    val isHost: Boolean,
    val isInRoom: Boolean,
)
