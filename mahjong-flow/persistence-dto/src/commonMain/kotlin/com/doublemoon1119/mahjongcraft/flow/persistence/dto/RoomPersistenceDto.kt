package com.doublemoon1119.mahjongcraft.flow.persistence.dto

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * 等待階段 [Room] 的完整權威 persistence DTO。
 *
 * @property id Room 與實體麻將桌共用的穩定 UUID。
 * @property hostId 房主玩家 UUID。
 * @property config 完整且帶穩定 type key 的規則配置。
 * @property playerIds Room 內所有人類與 AI 玩家 UUID。
 * @property readyPlayerIds 已準備玩家 UUID。
 * @property aiPlayerStrategyKeys AI 玩家 UUID 與策略登記 key。
 */
@Serializable
data class RoomPersistenceDto(
    val id: String,
    val hostId: String,
    val config: TypedPersistenceDto,
    val playerIds: Set<String>,
    val readyPlayerIds: Set<String>,
    val aiPlayerStrategyKeys: Map<String, String>,
)

/** 將 Room 權威領域狀態轉換成完整 persistence DTO。 */
fun Room.toPersistenceDto(
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    json: Json = Json,
): RoomPersistenceDto = RoomPersistenceDto(
    id = id.toString(),
    hostId = hostId.toString(),
    config = ruleConfigRegistry.encode(config, json),
    playerIds = playerIds.mapTo(linkedSetOf(), Uuid::toString),
    readyPlayerIds = readyPlayerIds.mapTo(linkedSetOf(), Uuid::toString),
    aiPlayerStrategyKeys = aiPlayerStrategyKeys.mapKeys { it.key.toString() },
)

/** 將 Room persistence DTO 驗證並還原成權威領域狀態。 */
fun RoomPersistenceDto.toDomain(
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    json: Json = Json,
): Room = Room(
    id = Uuid.parse(id),
    hostId = Uuid.parse(hostId),
    config = ruleConfigRegistry.decode(config, json),
    playerIds = playerIds.mapTo(linkedSetOf(), Uuid::parse),
    readyPlayerIds = readyPlayerIds.mapTo(linkedSetOf(), Uuid::parse),
    aiPlayerStrategyKeys = aiPlayerStrategyKeys.mapKeys { Uuid.parse(it.key) },
)
