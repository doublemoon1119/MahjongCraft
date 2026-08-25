package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/**
 * 為已耗盡思考時間的真人玩家產生固定自動操作。
 *
 * 反應視窗一律選擇 [GameAction.Pass]；自己回合優先摸切，尚未摸牌時執行機械摸牌。若玩家在鳴牌後
 * 等待捨牌期間才逾時，因為沒有剛摸入的牌，會固定捨出手牌中的第一張牌。
 *
 * @property gameRepository 讀取包含強制自動操作狀態的權威遊戲。
 */
@Factory
class ForcedAutoPlayDriver(
    private val gameRepository: GameRepository,
) {
    /**
     * 解析目前下一個必須由伺服器操作的玩家與命令。
     *
     * @param gameId 欲處理的遊戲。
     * @return 玩家與固定自動命令；目前沒有可處理決策、或對局已結束時為 null。
     */
    suspend fun resolveNextAction(gameId: Uuid): Pair<Uuid, GameCommand>? {
        val game = gameRepository.getGame(gameId) ?: return null
        if (game.isMatchOver) return null
        val state = game.tableState
        val forcedPlayerIds = game.forcedAutoPlayPlayerIds

        state.pendingKanReaction?.let { pending ->
            pending.eligiblePlayerIds.firstOrNull { it in forcedPlayerIds && it !in pending.responses }?.let { playerId ->
                return playerId to GameCommand.RespondToKan(GameAction.Pass)
            }
        }
        state.pendingReaction?.let { pending ->
            pending.eligiblePlayerIds.firstOrNull { it in forcedPlayerIds && it !in pending.responses }?.let { playerId ->
                return playerId to GameCommand.RespondToDiscard(GameAction.Pass)
            }
        }
        if (state.pendingKanReaction != null || state.pendingReaction != null) return null

        val currentPlayer = state.currentPlayer.takeIf { it.id in forcedPlayerIds } ?: return null
        if (currentPlayer.hand.lastDrawn == null && !currentPlayer.justClaimedMeld) {
            return currentPlayer.id to GameCommand.Draw
        }
        val discardedTileId = currentPlayer.hand.lastDrawn?.id ?: currentPlayer.hand.tiles.firstOrNull()?.id ?: return null
        return currentPlayer.id to GameCommand.Discard(discardedTileId)
    }
}
