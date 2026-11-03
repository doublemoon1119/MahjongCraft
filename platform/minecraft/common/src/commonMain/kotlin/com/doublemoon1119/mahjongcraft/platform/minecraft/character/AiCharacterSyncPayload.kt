package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlinx.serialization.Serializable

/**
 * 已授權桌子旁同步的角色外觀，只附目前追蹤實體的參照，不含私有規則資料。
 *
 * @property sessionId 這次載入的伺服器身分。
 * @property revision 來源的單調版本。
 * @property tableId 桌子識別碼。
 * @property matchId 對局場次；等待房間為 null。
 * @property available 外觀保存資料是否可用。
 * @property players 這份完整名冊的角色描述。
 * @property excludedPlayers 只有房主可讀的已排除角色頭像及名稱，不含模型參數。
 * @property worldEntityIds 目前已追蹤的成員實體 UUID，僅即時預覽使用。
 */
@Serializable
data class AiCharacterSyncPayload(
    val sessionId: String,
    val revision: Long,
    val tableId: String,
    val matchId: String?,
    val available: Boolean,
    val players: List<AiCharacterSnapshotNetworkDto>,
    val excludedPlayers: List<AiCharacterSnapshotNetworkDto>,
    val worldEntityIds: Map<String, String>,
) {
    /** 標準桌角色同步的線路上限。 */
    companion object {
        /** 標準桌最多四位參與者。 */
        const val MAX_PLAYERS: Int = 4

        /** 包含完整有界預覽參數的單桌封包上限。 */
        const val MAX_BYTES: Int = 512 * 1024
    }
}
