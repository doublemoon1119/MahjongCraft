package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import kotlin.uuid.Uuid

/**
 * 一張已公開、尚待規則決定後續處理的花牌。
 *
 * @property playerId 公開花牌的玩家。
 * @property tileId 已移到公開花區的牌張識別碼。
 */
data class TaiwanPendingFlowerFollowUp(
    val playerId: Uuid,
    val tileId: Uuid,
)
