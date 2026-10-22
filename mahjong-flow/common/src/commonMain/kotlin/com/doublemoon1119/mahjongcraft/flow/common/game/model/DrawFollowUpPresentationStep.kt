package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import kotlin.uuid.Uuid

/**
 * 摸牌後自動處理的一步，供平台依序呈現。
 *
 * 每一步都是把摸牌位上的牌移出手牌，再補一張牌到摸牌位；補到的牌是下一步要移出的牌，或這串處理結束後留在摸牌位的牌。
 *
 * @property action 自動執行的移出手牌動作。
 * @property setAsideTileId 這一步從摸牌位移出的牌。
 * @property drawnTileId 這一步補到的牌；其他玩家可以搶這張移出的牌、補牌暫停時為 null。
 * @property physicalWallTransitionPhases 這一步補牌造成的實體牌牆移動階段。
 * @property scoring 這一步移出當下的點數收付；不收付時為 null。
 */
data class DrawFollowUpPresentationStep(
    val action: GameAction.Extension,
    val setAsideTileId: Uuid,
    val drawnTileId: Uuid?,
    val physicalWallTransitionPhases: List<PhysicalWallLayoutTransitionPhase>,
    val scoring: AppliedActionScoring?,
)
