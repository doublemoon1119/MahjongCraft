package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PULL_NORTH_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpContext
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpDecision
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpPolicy

/**
 * 摸到北時自動拔北，把北當成摸到就要補的牌。
 *
 * 能不能拔與玩家自己宣告拔北時相同；摸到北卻不能拔，例如活牌只剩不到兩張而沒有牌能補進王牌時，以一般流局結束本局。
 *
 * 這是測試規則刻意的設計，與一般三人日麻不同：一般三人日麻摸到最後一張活牌後仍要打一張牌才荒牌流局，北也可以留著不拔；
 * 這裡把北當成摸到就一定要補的牌，沒牌可補時立即結束本局，用來在遊戲內驗證 [DrawFollowUpDecision.EndRound]。
 *
 * @property legalActionValidator 判斷這時能不能拔北的三人日麻合法動作判定器。
 */
internal class DebugAutoNorthDrawFollowUpPolicy(
    private val legalActionValidator: LegalActionValidator,
) : DrawFollowUpPolicy {
    override fun resolve(context: DrawFollowUpContext): DrawFollowUpDecision {
        if (context.drawnTile.tile.riichiCanonical != Tile.Honor.North) return DrawFollowUpDecision.None
        val legalActions = legalActionValidator.getLegalActions(LegalActionContext.ownTurn(context.tableState, context.playerId))
        return if (PULL_NORTH_GAME_ACTION in legalActions) {
            DrawFollowUpDecision.SetAside(PULL_NORTH_GAME_ACTION)
        } else {
            DrawFollowUpDecision.EndRound(RiichiExhaustiveDrawReason.Normal)
        }
    }
}
