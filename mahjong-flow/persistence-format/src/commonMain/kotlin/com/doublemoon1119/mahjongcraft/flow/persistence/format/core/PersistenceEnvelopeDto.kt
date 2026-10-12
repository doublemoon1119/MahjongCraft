package com.doublemoon1119.mahjongcraft.flow.persistence.format.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 包裝單一 MahjongCraft 伺服器權威狀態的版本化存檔格式。
 *
 * [state] 保持為 JSON 物件，讀取端可先檢查格式版本，再解碼權威狀態。
 *
 * @property schemaVersion [state] 使用的存檔 schema version。
 * @property state Room 與 Game 同批提交的伺服器權威狀態。
 */
@Serializable
data class PersistenceEnvelopeDto(
    val schemaVersion: Int,
    val state: JsonObject,
)
