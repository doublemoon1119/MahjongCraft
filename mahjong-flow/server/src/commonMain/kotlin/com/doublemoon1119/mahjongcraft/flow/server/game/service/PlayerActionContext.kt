package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import kotlin.uuid.Uuid

/** 指定玩家目前在牌局操作流程中取得的規則中立決策情境。 */
sealed interface PlayerActionContext {
    /** 取得決策權的玩家 Uuid。 */
    val playerId: Uuid

    /** 此操作情境對應的玩家決策階段。 */
    val phase: PlayerDecisionPhase

    /**
     * 玩家正在回應暗槓、加槓或移出手牌動作的搶和視窗。
     *
     * @property playerId 取得搶槓回應權的玩家 Uuid。
     * @property pending 目前的搶和反應視窗。
     */
    data class RobbingReaction(
        override val playerId: Uuid,
        val pending: PendingRobbingReaction,
    ) : PlayerActionContext {
        override val phase: PlayerDecisionPhase = PlayerDecisionPhase.ROBBING_REACTION
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
