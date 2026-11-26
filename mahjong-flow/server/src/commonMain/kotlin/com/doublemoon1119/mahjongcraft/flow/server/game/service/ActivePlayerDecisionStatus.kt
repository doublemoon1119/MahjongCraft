package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.DecisionTimeStatus
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import kotlin.uuid.Uuid

/**
 * 目前有效的玩家決策及其權威時間狀態。
 *
 * 此 read model 由 [GameDecisionTimerManager] 提供給 scheduler 與外層同步服務，不寫入 persistence，
 * 也不直接作為網路 DTO。
 *
 * @property playerId 目前具有決策權的玩家。
 * @property phase 玩家目前所處的決策階段。
 * @property time 此次決策在查詢時間點的基本思考時間與保留思考時間狀態。
 */
data class ActivePlayerDecisionStatus(
    val playerId: Uuid,
    val phase: PlayerDecisionPhase,
    val time: DecisionTimeStatus,
)
