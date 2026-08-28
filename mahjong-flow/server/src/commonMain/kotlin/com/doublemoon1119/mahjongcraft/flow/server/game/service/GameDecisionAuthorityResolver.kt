package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 依權威 [Game] 桌況解析目前需要做出決策的玩家與階段。 */
@Single
class GameDecisionAuthorityResolver {
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
     * @return 以玩家識別碼索引的決策階段，只包含真人玩家；對局已結束時一律回傳空 map，不再需要
     *   任何決策計時器。
     */
    fun resolve(game: Game): Map<Uuid, PlayerDecisionPhase> {
        if (game.isMatchOver) return emptyMap()
        val state = game.tableState
        val humanPlayerIds = state.players.filterNot { it.isAi }.mapTo(mutableSetOf()) { it.id }
        game.pendingRoundPreparation?.let { preparation ->
            return preparation.participantPlayerIds
                .filter { it in humanPlayerIds }
                .filterNot { it in preparation.completedPlayerIds }
                .filterNot { it in game.forcedAutoPlayPlayerIds }
                .associateWith { PlayerDecisionPhase.ROUND_PREPARATION }
        }
        state.pendingKanReaction?.let { pending ->
            return pending.eligiblePlayerIds
                .filter { it in humanPlayerIds }
                .filterNot { it in pending.responses }
                .filterNot { it in game.forcedAutoPlayPlayerIds }
                .associateWith { PlayerDecisionPhase.KAN_REACTION }
        }
        state.pendingReaction?.let { pending ->
            return pending.eligiblePlayerIds
                .filter { it in humanPlayerIds }
                .filterNot { it in pending.responses }
                .filterNot { it in game.forcedAutoPlayPlayerIds }
                .associateWith { PlayerDecisionPhase.DISCARD_REACTION }
        }

        val currentPlayer = state.currentPlayer
        return if (
            currentPlayer.id in humanPlayerIds &&
            currentPlayer.id !in game.forcedAutoPlayPlayerIds &&
            (currentPlayer.hand.lastDrawn != null || currentPlayer.justClaimedMeld)
        ) {
            mapOf(currentPlayer.id to PlayerDecisionPhase.OWN_TURN)
        } else {
            emptyMap()
        }
    }
}
