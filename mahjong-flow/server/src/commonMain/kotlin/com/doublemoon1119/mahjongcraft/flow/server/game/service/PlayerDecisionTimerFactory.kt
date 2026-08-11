package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionTimer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.startDecisionTimer
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/**
 * 依權威 [Game] 狀態及目前單調時間建立玩家決策計時器。
 *
 * @property clock 提供目前 runtime session 的單調時間。
 */
@Factory
class PlayerDecisionTimerFactory(
    private val clock: MonotonicClock,
) {
    /**
     * 為 [playerId] 建立一次新的基本思考時間與保留思考時間決策計時器。
     *
     * @param game 包含流程設定與玩家剩餘保留思考時間的權威遊戲狀態。
     * @param playerId 目前取得決策權的玩家。
     * @return 以目前時間開始的 [PlayerDecisionTimer]。
     */
    fun create(game: Game, playerId: Uuid): PlayerDecisionTimer = game.flowConfig.timeControl.startDecisionTimer(
        playerId = playerId,
        remainingReserveMillis = game.remainingReserveMillisByPlayerId.getValue(playerId),
        startedAtMillis = clock.nowMillis(),
    )
}
