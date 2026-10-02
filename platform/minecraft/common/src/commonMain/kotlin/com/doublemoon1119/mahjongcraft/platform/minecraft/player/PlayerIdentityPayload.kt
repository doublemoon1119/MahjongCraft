package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import kotlinx.serialization.Serializable

/**
 * 僅在已授權內容旁同步的有限玩家普通名稱，不包含皮膚或互動樣式。
 *
 * @property entries 此批內容所涉及的真人身分。
 */
@Serializable
data class PlayerIdentityPayload(val entries: List<PlayerIdentityEntry>) {
    /** 名稱同步的容量與格式限制。 */
    companion object {
        /** 單批最多同步的相關玩家數量。 */
        const val MAX_ENTRIES = 64

        /** 普通玩家名稱的最大字元數。 */
        const val MAX_NAME_LENGTH = 16

        /** 包含 JSON 包裝的單批線路大小上限。 */
        const val MAX_BYTES = 8192
    }
}

/**
 * 真人玩家的呈現身分，UUID 仍是權威識別。
 *
 * @property playerId 玩家的 UUID 字串。
 * @property name 伺服器保存的最後已知普通名稱。
 */
@Serializable
data class PlayerIdentityEntry(val playerId: String, val name: String)
