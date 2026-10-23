package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import kotlin.uuid.Uuid

/**
 * 摸牌後自動處理的一步，供平台依序呈現。
 *
 * 每一步都是把摸牌位上的牌移出手牌，再依規則補牌；規則一次補多張時，前面的牌併入立牌，最後一張放到摸牌位。補到的牌
 * 可能在之後的步驟被移出（移出前會先換到摸牌位），或在這串處理結束後留在手上。
 *
 * @property action 自動執行的移出手牌動作。
 * @property setAsideTileId 這一步從摸牌位移出的牌。
 * @property drawnTileIds 這一步依摸取順序補到的牌；其他玩家可以搶這張移出的牌、補牌暫停時為空清單。
 * @property physicalWallTransitionPhases 這一步補牌造成的實體牌牆移動階段。
 * @property scoring 這一步移出當下的點數收付；不收付時為 null。
 */
data class DrawFollowUpPresentationStep(
    val action: GameAction.Extension,
    val setAsideTileId: Uuid,
    val drawnTileIds: List<Uuid>,
    val physicalWallTransitionPhases: List<PhysicalWallLayoutTransitionPhase>,
    val scoring: AppliedActionScoring?,
)
