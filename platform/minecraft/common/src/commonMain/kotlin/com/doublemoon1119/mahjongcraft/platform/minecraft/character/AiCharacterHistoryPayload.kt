package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlinx.serialization.Serializable

/**
 * 已授權摘要回覆附帶的角色描述，不含世界實體參照。
 *
 * @property requestId 對應的摘要查詢配對鍵。
 * @property matchId 保存時的場次。
 * @property players 該場可取得的固定描述。
 */
@Serializable
data class AiCharacterHistoryPayload(
    val requestId: String,
    val matchId: String,
    val players: List<AiCharacterSnapshotNetworkDto>,
)
