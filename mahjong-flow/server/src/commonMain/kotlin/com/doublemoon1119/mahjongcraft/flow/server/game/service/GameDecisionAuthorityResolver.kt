package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import kotlin.uuid.Uuid

/**
 * 依權威 [Game] 桌況解析目前需要做出決策的玩家與階段。
 *
 * @property actionContextResolver 玩家目前操作情境的權威解析器。
 * @property automaticDecisionEvaluator 檢查已啟用自動操作是否能立即執行；未提供時不可在本解析器中評估此類玩家。
 */
class GameDecisionAuthorityResolver(
    private val actionContextResolver: PlayerActionContextResolver = PlayerActionContextResolver(),
    private val automaticDecisionEvaluator: AutomaticDecisionEvaluator? = null,
) {
    /**
     * 解析目前所有尚未完成的玩家決策。
     *
     * reaction 與 chankan 視窗可同時包含多位玩家；一般回合只有目前玩家，且尚未執行的機械摸牌
     * 不視為需要思考時間的決策。
     *
     * 「思考時間」這個概念只對真人玩家有意義——AI 的決策由 `AiTurnDriver` 在 [GameFlowCoordinator.driveAutomatedPlayers]
     * 內同步解析，不涉及等待，因此這裡一律排除 AI 玩家；否則 AI 玩家會被誤判成需要建立決策計時器，
     * 一旦這段等待真的耗盡保留思考時間，就會被誤標記進 `forcedAutoPlayPlayerIds`，改由
     * `ForcedAutoPlayDriver` 的固定邏輯接管，而不是它自己的 AI 策略。
     *
     * @param game 目前的權威遊戲狀態。
     * @return 以玩家識別碼索引的決策階段，只包含真人玩家；對局已結束時一律回傳空 map。規則選擇視窗
     *   開著時只回傳尚未提交的參與者，即使本局已排定結算交接也一樣；沒有視窗而已進入結算交接時回傳空 map。
     */
    fun resolve(game: Game): Map<Uuid, PlayerDecisionPhase> {
        if (game.isMatchOver) return emptyMap()
        val state = game.tableState
        val humanPlayerIds = state.players.filterNot { game.isAi(it.id) }.mapTo(mutableSetOf()) { it.id }
        game.pendingRuleDecision?.let { decision ->
            return decision.participantPlayerIds
                .filter { it in humanPlayerIds }
                .filterNot { it in decision.completedPlayerIds }
                .filterNot { it in game.forcedAutoPlayPlayerIds }
                .associateWith { PlayerDecisionPhase.RULE_DECISION }
        }
        if (game.pendingTransition != null) return emptyMap()
        return actionContextResolver.resolve(state)
            .filterKeys { it in humanPlayerIds && it !in game.forcedAutoPlayPlayerIds }
            .filterKeys { playerId ->
                if (game.enabledAutomaticControlIdsByPlayerId[playerId].isNullOrEmpty()) {
                    true
                } else {
                    requireNotNull(automaticDecisionEvaluator) {
                        "AutomaticDecisionEvaluator is required when automatic controls are enabled"
                    }.evaluate(game, playerId)?.immediateAction == null
                }
            }
            .mapValues { it.value.phase }
    }
}
