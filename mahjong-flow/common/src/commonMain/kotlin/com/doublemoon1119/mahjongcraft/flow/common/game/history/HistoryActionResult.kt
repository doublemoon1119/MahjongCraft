package com.doublemoon1119.mahjongcraft.flow.common.game.history

import kotlin.uuid.Uuid

/**
 * 動作提交後的權威結果，不從玩家通知或各人本局 action history 回推。
 *
 * @property affectedTileIds 動作直接涉及的牌 UUID。
 * @property newlyRevealedTileIds 本次交易新增公開的牌 UUID。
 * @property remainingWallTileCount 提交後活牌牆的剩餘張數。
 * @property reservedWallTileIds 提交後保留牌區的牌 UUID，依規則順序排列。
 * @property scoresByPlayerId 提交後各玩家分數。
 * @property nextPlayerId 提交後輪到的玩家 UUID。
 */
data class HistoryActionResult(
    val affectedTileIds: List<Uuid>,
    val newlyRevealedTileIds: List<Uuid>,
    val remainingWallTileCount: Int,
    val reservedWallTileIds: List<Uuid>,
    val scoresByPlayerId: Map<Uuid, Int>,
    val nextPlayerId: Uuid,
)
