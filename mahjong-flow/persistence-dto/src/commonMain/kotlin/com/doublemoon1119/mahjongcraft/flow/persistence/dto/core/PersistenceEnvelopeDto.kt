package com.doublemoon1119.mahjongcraft.flow.persistence.dto.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 包裝單一世界級 MahjongCraft 權威狀態的版本化存檔格式。
 *
 * [state] 保持為 JSON object，讓 migration 能在反序列化成當前 DTO 前轉換舊 schema。
 *
 * @property schemaVersion [state] 使用的存檔 schema version。
 * @property state Room 與 Game 同批提交的世界級權威狀態。
 */
@Serializable
data class PersistenceEnvelopeDto(
    val schemaVersion: Int,
    val state: JsonObject,
)
