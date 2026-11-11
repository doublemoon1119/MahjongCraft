package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.AppliedActionScoring
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import kotlin.uuid.Uuid

/** [TableStep] 中發生的事；只描述事件本身，呈現方式由呈現端決定。 */
sealed interface TableStepEvent {
    /**
     * 玩家宣告的動作成立，例如吃、碰、槓、立直、自摸、榮和或移出手牌。
     *
     * @property actorId 宣告動作的玩家。
     * @property action 成立的動作。
     */
    data class ActionDeclared(
        val actorId: Uuid,
        val action: GameAction,
    ) : TableStepEvent

    /**
     * 動作成立當下的點數收付。
     *
     * @property scoring 已套用的收付。
     */
    data class ActionScored(
        val scoring: AppliedActionScoring,
    ) : TableStepEvent

    /**
     * 規則依序重排實體牌牆中的牌，例如補牌後把牌補回規則保留區。各階段依序完成，最終位置與這一步桌況的實體牌牆布局一致。
     *
     * @property phases 依規則決定順序排列的移動階段。
     */
    data class WallRearranged(
        val phases: List<PhysicalWallLayoutTransitionPhase>,
    ) : TableStepEvent

    /**
     * 玩家整理手牌：先前直接補進手牌、還沒理過的牌收進手牌。例如局首逐輪補完牌之後，各家一起理牌。
     *
     * 啟用自動理牌的玩家，權威手牌依規則牌序重新整理。
     *
     * @property playerIds 整理手牌的玩家。
     */
    data class HandsSorted(
        val playerIds: Set<Uuid>,
    ) : TableStepEvent
}
