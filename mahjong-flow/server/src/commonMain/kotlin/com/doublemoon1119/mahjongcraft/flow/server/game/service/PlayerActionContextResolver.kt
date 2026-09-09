package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.logic.table.PendingKanReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 指定玩家目前在牌局操作流程中取得的規則中立決策情境。 */
sealed interface PlayerActionContext {
    /** 取得決策權的玩家 Uuid。 */
    val playerId: Uuid

    /** 此操作情境對應的玩家決策階段。 */
    val phase: PlayerDecisionPhase

    /**
     * 玩家正在回應暗槓或加槓的搶槓視窗。
     *
     * @property playerId 取得搶槓回應權的玩家 Uuid。
     * @property pending 目前的搶槓反應視窗。
     */
    data class KanReaction(
        override val playerId: Uuid,
        val pending: PendingKanReaction,
    ) : PlayerActionContext {
        override val phase: PlayerDecisionPhase = PlayerDecisionPhase.KAN_REACTION
    }

    /**
     * 玩家正在回應其他玩家的捨牌。
     *
     * @property playerId 取得捨牌回應權的玩家 Uuid。
     * @property pending 目前的捨牌反應視窗。
     */
    data class DiscardReaction(
        override val playerId: Uuid,
        val pending: PendingReaction,
    ) : PlayerActionContext {
        override val phase: PlayerDecisionPhase = PlayerDecisionPhase.DISCARD_REACTION
    }

    /**
     * 玩家自己的回合，已完成摸牌或剛完成吃碰而需要行動。
     *
     * @property playerId 目前回合玩家 Uuid。
     */
    data class OwnTurn(
        override val playerId: Uuid,
    ) : PlayerActionContext {
        override val phase: PlayerDecisionPhase = PlayerDecisionPhase.OWN_TURN
    }
}

/** 依權威桌況解析目前最高優先操作情境中的所有玩家。 */
@Single
class PlayerActionContextResolver {
    /**
     * 解析目前取得操作權的所有玩家，優先順序為搶槓反應、捨牌反應、自己回合。
     *
     * 此解析只描述桌況事實，不過濾 AI、強制自動操作玩家，也不處理局前準備或對局生命週期。
     */
    fun resolve(state: TableState): Map<Uuid, PlayerActionContext> {
        state.pendingKanReaction?.let { pending ->
            return pending.eligiblePlayerIds
                .filterNot { it in pending.responses }
                .associateWith { PlayerActionContext.KanReaction(it, pending) }
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

    /** 解析指定玩家目前的操作情境；沒有取得操作權時回傳 null。 */
    fun resolveFor(state: TableState, playerId: Uuid): PlayerActionContext? = resolve(state)[playerId]
}
