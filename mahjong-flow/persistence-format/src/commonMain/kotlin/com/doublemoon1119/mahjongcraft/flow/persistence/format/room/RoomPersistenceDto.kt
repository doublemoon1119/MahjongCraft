package com.doublemoon1119.mahjongcraft.flow.persistence.format.room

import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.GameFlowConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import kotlinx.serialization.Serializable

/**
 * 等待階段 [Room] 的完整權威 persistence DTO。
 *
 * @property id Room 與場地共用的穩定 UUID。
 * @property hostId 房主玩家 UUID。
 * @property config 完整且帶穩定 type key 的規則配置。
 * @property flowConfig 不影響麻將規則的流程與觀看設定。
 * @property playerIds Room 內所有人類與 AI 玩家 UUID，依加入順序排列。
 * @property readyPlayerIds 已準備玩家 UUID。
 * @property aiPlayerStrategyKeys AI 玩家 UUID 與策略登記 key。
 */
@Serializable
data class RoomPersistenceDto(
    val id: String,
    val hostId: String,
    val config: TypedPersistenceDto,
    val flowConfig: GameFlowConfigPersistenceDto,
    val playerIds: List<String>,
    val readyPlayerIds: List<String>,
    val aiPlayerStrategyKeys: Map<String, String>,
)
