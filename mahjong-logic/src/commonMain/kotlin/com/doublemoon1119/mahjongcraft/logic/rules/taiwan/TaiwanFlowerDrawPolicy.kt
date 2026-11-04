package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpContext
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpDecision
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpPolicy

/** 台灣麻將摸到花牌後自動補花的規則。 */
object TaiwanFlowerDrawPolicy : DrawFollowUpPolicy {
    /**
     * 花牌尚有合法補牌時移出手牌，牌牆已無可補牌時結束本局。
     *
     * @param context 剛完成的摸牌與目前桌況。
     * @return 花牌的後續處理，或一般牌不需處理。
     */
    override fun resolve(context: DrawFollowUpContext): DrawFollowUpDecision {
        if (!TaiwanTileTypes.isFlower(context.drawnTile.tile)) return DrawFollowUpDecision.None
        if (context.tableState.tileWall.remainingCount == 0) {
            return DrawFollowUpDecision.EndRound(TaiwanExhaustiveDrawReason.NoSupplementalTile)
        }
        return DrawFollowUpDecision.SetAsideThenDecide(TAIWAN_REVEAL_FLOWER_ACTION)
    }
}

/** 自動公開花牌並補牌的規則動作。 */
val TAIWAN_REVEAL_FLOWER_ACTION: GameAction.Extension = GameAction.Extension(TaiwanGameAction.RevealFlower)
