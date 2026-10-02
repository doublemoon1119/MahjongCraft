package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import kotlin.uuid.Uuid

/** 歷史 Replay 對局識別資料。
 * @property matchId 對局識別碼。
 * @property tableId 牌桌識別碼。
 * @property players 依初始座位排序的玩家識別資料。
 */
data class HistoryReplayIdentity(
    val matchId: Uuid,
    val tableId: Uuid,
    val players: List<HistoryReplayPlayerIdentity>,
)

/** 歷史 Replay 玩家識別資料。
 * @property initialSeatIndex 初始座位索引。
 * @property playerId 玩家識別碼。
 * @property aiStrategyKey AI 策略識別碼。
 */
data class HistoryReplayPlayerIdentity(
    val initialSeatIndex: Int,
    val playerId: Uuid,
    val aiStrategyKey: String?,
)
