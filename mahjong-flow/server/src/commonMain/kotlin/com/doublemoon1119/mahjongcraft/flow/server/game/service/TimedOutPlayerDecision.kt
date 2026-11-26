package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import kotlin.uuid.Uuid

/**
 * scheduler 已取得處理權的一次完整逾時決策。
 *
 * @property gameId 發生逾時的遊戲。
 * @property playerId 耗盡全部思考時間的玩家。
 * @property phase 發生逾時時的決策階段。
 */
data class TimedOutPlayerDecision(
    val gameId: Uuid,
    val playerId: Uuid,
    val phase: PlayerDecisionPhase,
)
