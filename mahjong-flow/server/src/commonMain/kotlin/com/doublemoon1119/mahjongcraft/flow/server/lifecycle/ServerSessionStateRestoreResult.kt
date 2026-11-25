package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

/**
 * 伺服器工作階段衍生狀態的恢復結果。
 *
 * @property membershipConflicts 無法自動建立唯一場地歸屬的玩家衝突清單。
 * @property snapshotFailures 已隔離並採保守顯示的對局投影失敗。
 * @property scheduleFailures 採保守顯示的對局中，重新安排決策計時器與 AI 失敗的對局。
 */
data class ServerSessionStateRestoreResult(
    val membershipConflicts: List<PlayerMembershipConflict>,
    val snapshotFailures: List<GameSnapshotRestoreFailure>,
    val scheduleFailures: List<GameDecisionScheduleRestoreFailure>,
)
