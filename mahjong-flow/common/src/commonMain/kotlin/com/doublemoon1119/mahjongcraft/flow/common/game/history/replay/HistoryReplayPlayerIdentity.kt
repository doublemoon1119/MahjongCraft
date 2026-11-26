package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import kotlin.uuid.Uuid

/**
 * 歷史 Replay 玩家識別資料。
 *
 * @property initialSeatIndex 初始座位索引。
 * @property playerId 玩家識別碼。
 * @property aiStrategyKey AI 策略識別碼。
 */
data class HistoryReplayPlayerIdentity(
    val initialSeatIndex: Int,
    val playerId: Uuid,
    val aiStrategyKey: String?,
)
