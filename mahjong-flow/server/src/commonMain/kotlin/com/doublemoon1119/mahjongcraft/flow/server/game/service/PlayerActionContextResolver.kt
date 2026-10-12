package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 依權威桌況解析目前最高優先操作情境中的所有玩家。 */
@Single
class PlayerActionContextResolver {
    /**
     * 解析目前取得操作權的所有玩家，優先順序為搶和反應、捨牌反應、自己回合。
     *
     * 此解析只描述桌況事實，不過濾 AI、強制自動操作玩家，也不處理局前準備或對局生命週期。
     *
     * @param state 欲解析的權威桌況。
     * @return 以玩家識別碼索引的操作情境；沒有待處理決策時為空 map。
     */
    fun resolve(state: TableState): Map<Uuid, PlayerActionContext> {
        state.pendingRobbingReaction?.let { pending ->
            return pending.eligiblePlayerIds
                .filterNot { it in pending.responses }
                .associateWith { PlayerActionContext.RobbingReaction(it, pending) }
        }
        state.pendingReaction?.let { pending ->
            return pending.eligiblePlayerIds
                .filterNot { it in pending.responses }
                .associateWith { PlayerActionContext.DiscardReaction(it, pending) }
        }

        val currentPlayer = state.currentPlayer
        return if (currentPlayer.hand.lastDrawn != null || currentPlayer.justClaimedMeld) {
            mapOf(currentPlayer.id to PlayerActionContext.OwnTurn(currentPlayer.id))
        } else {
            emptyMap()
        }
    }

    /**
     * 解析指定玩家目前的操作情境；沒有取得操作權時回傳 null。
     *
     * @param state 欲解析的權威桌況。
     * @param playerId 欲查詢的玩家識別碼。
     * @return 該玩家目前的操作情境；沒有取得操作權時為 null。
     */
    fun resolveFor(state: TableState, playerId: Uuid): PlayerActionContext? = resolve(state)[playerId]
}
