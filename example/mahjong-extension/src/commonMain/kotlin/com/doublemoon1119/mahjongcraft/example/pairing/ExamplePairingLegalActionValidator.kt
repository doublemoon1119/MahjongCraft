package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingDeclarationAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 判定簡易配對麻將的自摸與每局一次的宣告動作。
 *
 * 自己回合的摸牌可由 incomingTile 單獨提供，或保存在手牌的 lastDrawn；
 * 判定時將該牌放在 lastDrawn 一次，不另外加入立牌。
 */
class ExamplePairingLegalActionValidator : LegalActionValidator {
    override fun getLegalActions(
        tableState: TableState,
        player: MahjongPlayer,
        sourceAction: GameAction,
        sourceDirection: RelativeDirection,
        incomingTile: IdentifiedTile?,
    ): List<GameAction> {
        if (sourceDirection != RelativeDirection.Self) return listOf(GameAction.Pass)
        if (player.id != tableState.currentPlayer.id) return emptyList()
        val drawnTile = incomingTile ?: player.hand.lastDrawn ?: return emptyList()
        val handWithDrawnTile = player.hand.copy(lastDrawn = drawnTile)

        val ruleState = player.playerRuleState as? ExamplePairingPlayerState
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
