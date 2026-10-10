package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

/**
 * server session 衍生狀態恢復結果。
 *
 * @property membershipConflicts 無法自動建立唯一場地歸屬的玩家衝突清單。
 */
data class ServerSessionStateRestoreResult(
    val membershipConflicts: List<PlayerMembershipConflict>,
)
