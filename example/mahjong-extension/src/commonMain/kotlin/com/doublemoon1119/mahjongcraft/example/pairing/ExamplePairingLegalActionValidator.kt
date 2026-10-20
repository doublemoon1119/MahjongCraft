package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingDeclarationAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult

/**
 * 判定簡易配對麻將的自摸與每局一次的宣告動作。
 *
 * 判定時使用 [LegalActionContext.handWithIncoming]，由共用判定資料統一提供包含來源牌的手牌。
 */
class ExamplePairingLegalActionValidator : LegalActionValidator {
    override fun getLegalActions(context: LegalActionContext): List<GameAction> {
        if (context.sourceDirection != RelativeDirection.Self) {
            return listOf(GameAction.Pass)
        }
        if (context.player.id != context.tableState.currentPlayer.id) return emptyList()
        if (context.incomingTile == null) return emptyList()
        val handWithDrawnTile = context.handWithIncoming

        val ruleState = context.player.playerRuleState as? ExamplePairingPlayerState
        val actions = buildList {
            if (ExamplePairingShantenCalculator().calculate(handWithDrawnTile) is ShantenResult.Complete) {
                add(GameAction.Tsumo)
            }
            if (ruleState?.declared != true) {
                add(GameAction.Extension(ExamplePairingDeclarationAction))
            }
        }
        return actions
    }
}
