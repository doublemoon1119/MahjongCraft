package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import kotlin.uuid.Uuid

/**
 * 以保守快照恢復的對局，重新安排決策計時器與 AI 時失敗。
 *
 * - 這局可能停在目前的決策，等玩家操作才繼續。
 *
 * @property gameId 無法重新安排的對局。
 * @property cause 重新安排失敗的原因，只提供給伺服器診斷。
 */
data class GameDecisionScheduleRestoreFailure(
    val gameId: Uuid,
    val cause: Throwable,
)
