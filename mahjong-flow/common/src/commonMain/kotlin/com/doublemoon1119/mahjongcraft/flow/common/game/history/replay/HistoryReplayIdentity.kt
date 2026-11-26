package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import kotlin.uuid.Uuid

/**
 * 歷史 Replay 對局識別資料。
 *
 * @property matchId 對局識別碼。
 * @property venueId 場地識別碼。
 * @property players 依初始座位排序的玩家識別資料。
 */
data class HistoryReplayIdentity(
    val matchId: Uuid,
    val venueId: Uuid,
    val players: List<HistoryReplayPlayerIdentity>,
)
