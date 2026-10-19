package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingDeclarationAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 判定簡易配對麻將的自摸與一次性示範宣告動作。 */
class ExamplePairingLegalActionValidator : LegalActionValidator {
    override fun getLegalActions(
        tableState: TableState,
        player: MahjongPlayer,
        sourceAction: GameAction,
        sourceDirection: RelativeDirection,
        incomingTile: IdentifiedTile?,
    ): List<GameAction> {
        if (sourceDirection != RelativeDirection.Self) return listOf(GameAction.Pass)
        if (player.id != tableState.currentPlayer.id || player.hand.lastDrawn == null) return emptyList()

        val ruleState = player.playerRuleState as? ExamplePairingPlayerState
        val actions = buildList {
            if (ExamplePairingShantenCalculator().calculate(player.hand) is ShantenResult.Complete) {
                add(GameAction.Tsumo)
            }
            if (ruleState?.declared != true) {
                add(GameAction.Extension(ExamplePairingDeclarationAction))
            }
        }
        return actions
    }
}
