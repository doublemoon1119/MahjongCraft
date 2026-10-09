package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/**
 * 伺服器代為決定時使用的固定命令：真人耗盡思考時間，或 AI 沒有在等待上限內得到策略結果時使用。
 *
 * 反應視窗一律選擇 [GameAction.Pass]；自己回合優先摸切，尚未摸牌時執行機械摸牌。若玩家在鳴牌後
 * 等待捨牌期間才需要代打，因為沒有剛摸入的牌，會固定捨出手牌中的第一張牌。
 *
 * @param state 目前的桌況。
 * @param playerId 需要代打的玩家。
 * @return 固定命令；[playerId] 目前沒有可處理的決策時為 null。
 */
fun fixedAutoPlayCommand(state: TableState, playerId: Uuid): GameCommand? {
    val robbing = state.pendingRobbingReaction
    if (robbing != null && playerId in robbing.eligiblePlayerIds && playerId !in robbing.responses) {
        return GameCommand.RespondToRobbing(GameAction.Pass)
    }
    val reaction = state.pendingReaction
    if (reaction != null && playerId in reaction.eligiblePlayerIds && playerId !in reaction.responses) {
        return GameCommand.RespondToDiscard(GameAction.Pass)
    }
    if (robbing != null || reaction != null) return null
    val currentPlayer = state.currentPlayer.takeIf { it.id == playerId } ?: return null
    if (currentPlayer.hand.lastDrawn == null && !currentPlayer.justClaimedMeld) return GameCommand.Draw
    val discardedTileId = currentPlayer.hand.lastDrawn?.id ?: currentPlayer.hand.tiles.firstOrNull()?.id ?: return null
    return GameCommand.Discard(discardedTileId)
}

/**
 * 為已耗盡思考時間的真人玩家產生固定自動操作（見 [fixedAutoPlayCommand]）。
 *
 * 依序在搶槓、捨牌反應視窗中找出第一位尚未回應、且已進入強制自動操作的玩家；兩種視窗都沒有時才看目前行動的玩家。
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
        val robbing = state.pendingRobbingReaction
        val reaction = state.pendingReaction
        val playerId = robbing?.eligiblePlayerIds?.firstOrNull { it in forcedPlayerIds && it !in robbing.responses }
            ?: reaction?.eligiblePlayerIds?.firstOrNull { it in forcedPlayerIds && it !in reaction.responses }
            ?: state.currentPlayer.id.takeIf { it in forcedPlayerIds && robbing == null && reaction == null }
            ?: return null
        return fixedAutoPlayCommand(state, playerId)?.let { playerId to it }
    }
}
