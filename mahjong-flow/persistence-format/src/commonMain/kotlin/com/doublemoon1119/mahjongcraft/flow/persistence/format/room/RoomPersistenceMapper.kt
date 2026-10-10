package com.doublemoon1119.mahjongcraft.flow.persistence.format.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * 將 Room 權威領域狀態轉換成完整 persistence DTO。
 *
 * @param ruleConfigRegistry 規則設定的持久化轉換註冊表。
 * @param json 供註冊的轉換器編解碼擴充資料使用的 JSON 編解碼器。
 * @return 包含原模型資料的持久化 DTO。
 */
fun Room.toPersistenceDto(
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    json: Json = Json,
): RoomPersistenceDto = RoomPersistenceDto(
    id = id.toString(),
    hostId = hostId.toString(),
    config = ruleConfigRegistry.encode(gameConfig.ruleConfig, json),
    flowConfig = gameConfig.flowConfig.toPersistenceDto(),
    playerIds = playerIds.map(Uuid::toString),
    readyPlayerIds = readyPlayerIds.map(Uuid::toString),
    aiPlayerStrategyKeys = aiPlayerStrategyKeys.mapKeys { it.key.toString() },
)

/**
 * 將 Room persistence DTO 驗證並還原成權威領域狀態。
 *
 * @param ruleConfigRegistry 規則設定的持久化轉換註冊表。
 * @param json 供註冊的轉換器編解碼擴充資料使用的 JSON 編解碼器。
 * @return 還原的領域模型。
 */
fun RoomPersistenceDto.toDomain(
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    json: Json = Json,
): Room = Room(
    id = Uuid.parse(id),
    hostId = Uuid.parse(hostId),
    gameConfig = GameConfig(
        ruleConfig = ruleConfigRegistry.decode(config, json),
        flowConfig = flowConfig.toDomain(),
    ),
    playerIds = playerIds.map(Uuid::parse),
    readyPlayerIds = readyPlayerIds.map(Uuid::parse),
    aiPlayerStrategyKeys = aiPlayerStrategyKeys.mapKeys { Uuid.parse(it.key) },
)
