package com.doublemoon1119.mahjongcraft.flow.persistence.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 以穩定 type key 包裝可由第三方擴充、並由 [PersistenceDtoRegistry] 轉換的 persistence DTO payload。
 *
 * @property typeKey 由規則模組註冊、跨存檔版本維持穩定的型別識別字串。
 * @property payload 對應具體 persistence DTO 的完整 JSON object。
 */
@Serializable
data class TypedPersistenceDto(
    val typeKey: String,
    val payload: JsonObject,
)
