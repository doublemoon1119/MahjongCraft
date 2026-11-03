package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlinx.serialization.Serializable

/**
 * 一名 AI 的公開外觀線路資料。
 *
 * @property playerId 遊戲成員編號，不是原生實體編號。
 * @property characterId 完整角色識別碼。
 * @property definition 已擷取的描述；缺少描述時為 null。
 * @property displayName 當場普通名稱，沒有時為 null。
 */
@Serializable
data class AiCharacterSnapshotNetworkDto(
    val playerId: String,
    val characterId: String,
    val definition: AiCharacterDefinitionNetworkDto?,
    val displayName: String?,
)
