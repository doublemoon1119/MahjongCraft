package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionTimer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.startDecisionTimer
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * 依權威 [Game] 狀態及目前單調時間建立玩家決策計時器。
 *
 * @property clock 提供目前 runtime session 的單調時間；實作由平台層提供，理由見 [MonotonicClock] KDoc。
 */
class PlayerDecisionTimerFactory(
    private val clock: MonotonicClock,
) {
    /**
     * 為 [playerId] 建立一次基本思考時間與保留思考時間決策計時器。
     *
     * @param game 包含流程設定與玩家剩餘保留思考時間的權威遊戲狀態。
     * @param playerId 目前取得決策權的玩家。
     * @param phase 這次決策所屬的階段，決定基本思考秒數採用 [GameFlowConfig.timeControl]
     *   或規則提供的 [GameFlowConfig.ruleDecision]。
     *   [PlayerDecisionPhase.RULE_DECISION] 完全不動用共用保留思考時間池——固定以 0 保留思考時間
     *   起算，規則選擇時間用完就直接逾時；[GameDecisionTimerManager] 結算這個階段的計時器
     *   時，同樣不會把結算結果寫回共用池，玩家在其他階段已經存下的保留思考時間不會被這個階段消耗，
     *   也不會被清空。
     * @param resumedBase 接續上一個 session 被中斷的同一次決策時，其尚未使用的基本思考
     *   時間；`null` 代表這是一次全新的決策。
     * @return 以目前時間開始的 [PlayerDecisionTimer]。
     */
    fun create(
        game: Game,
        playerId: Uuid,
        phase: PlayerDecisionPhase,
        resumedBase: Duration? = null,
    ): PlayerDecisionTimer {
        val baseSeconds: Int
        val remainingReserve: Duration
        if (phase == PlayerDecisionPhase.RULE_DECISION) {
            baseSeconds = requireNotNull(game.flowConfig.ruleDecision) {
                "Rule decision requires a ruleDecision time configuration"
            }.baseSeconds
            remainingReserve = Duration.ZERO
        } else {
            baseSeconds = game.flowConfig.timeControl.baseSeconds
            remainingReserve = game.remainingReserveByPlayerId.getValue(playerId)
        }
        return startDecisionTimer(
            baseSeconds = baseSeconds,
            playerId = playerId,
            remainingReserve = remainingReserve,
            startedAt = clock.now(),
            resumedBase = resumedBase,
        )
    }
}
