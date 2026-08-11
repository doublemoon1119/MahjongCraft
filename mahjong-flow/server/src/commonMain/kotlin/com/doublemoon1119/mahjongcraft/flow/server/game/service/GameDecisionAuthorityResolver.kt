package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
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
     * @param game 目前的權威遊戲狀態。
     * @return 以玩家識別碼索引的決策階段。
     */
    fun resolve(game: Game): Map<Uuid, PlayerDecisionPhase> {
        val state = game.tableState
        state.pendingChankan?.let { pending ->
            return pending.eligiblePlayerIds
                .filterNot { it in pending.responses }
                .filterNot { it in game.forcedAutoPlayPlayerIds }
                .associateWith { PlayerDecisionPhase.CHANKAN_REACTION }
        }
        state.pendingReaction?.let { pending ->
            return pending.eligiblePlayerIds
                .filterNot { it in pending.responses }
                .filterNot { it in game.forcedAutoPlayPlayerIds }
                .associateWith { PlayerDecisionPhase.DISCARD_REACTION }
        }

        val currentPlayer = state.currentPlayer
        val justClaimedMeld = currentPlayer.actionHistory.lastOrNull().let { action ->
            action is GameAction.Chi || action is GameAction.Pon
        }
        return if (
            currentPlayer.id !in game.forcedAutoPlayPlayerIds &&
            (currentPlayer.hand.lastDrawn != null || justClaimedMeld)
        ) {
            mapOf(currentPlayer.id to PlayerDecisionPhase.OWN_TURN)
        } else {
            emptyMap()
        }
    }
}
