package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import kotlin.uuid.Uuid

/**
 * 無法自動恢復的單一玩家跨桌歸屬。
 *
 * @property playerId 發生跨場地重複歸屬的玩家識別碼。
 * @property venueIds 該玩家同時出現的場地識別碼集合。
 */
data class PlayerMembershipConflict(
    val playerId: Uuid,
    val venueIds: Set<Uuid>,
)
