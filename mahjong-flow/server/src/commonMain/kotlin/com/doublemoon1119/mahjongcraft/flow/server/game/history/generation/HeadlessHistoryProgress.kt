package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import kotlinx.coroutines.flow.flow
import kotlin.uuid.Uuid

/**
 * 交付收集端的一筆完整交易或已排空的終點。
 *
 * @property snapshot 來源已提交的權威快照。
 * @property game 目前來源對局；返回房間後為 null。
 * @property matchId 整場識別碼。
 * @property venueId 隔離場地識別碼。
 * @property terminal 僅在返回房間且所有事件已確認後為 true。
 * @property events 本批完整權威交易；終點批次為空。
 */
data class HeadlessHistoryProgress(
    val snapshot: AuthoritativeStateSnapshot,
    val game: Game?,
    val matchId: Uuid,
    val venueId: Uuid,
    val terminal: Boolean,
    val events: List<HistoryOutboxEvent>,
)
