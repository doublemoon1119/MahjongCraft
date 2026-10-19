package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueContextCalculator
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 建立簡易配對麻將的胡牌計算上下文。 */
class ExamplePairingHandValueContextCalculator : HandValueContextCalculator<ExamplePairingHandValueContext, ExamplePairingHandValueContextCalculator.Input> {
    /**
     * 胡牌計算所需的局面輸入。
     *
     * @property tableState 胡牌當下的完整桌況。
     * @property player 胡牌玩家。
     * @property incomingTile 本次讓手牌完成的牌。
     * @property isTsumo 是否為自摸。
     */
    data class Input(
        val tableState: TableState,
        val player: MahjongPlayer,
        val incomingTile: IdentifiedTile,
        val isTsumo: Boolean,
    )

    override fun calculate(input: Input): ExamplePairingHandValueContext = ExamplePairingHandValueContext(
        hand = input.player.hand,
        winningTile = input.incomingTile.tile,
        isTsumo = input.isTsumo,
        isMenzen = input.player.hand.exposedMelds.isEmpty(),
        roundWind = input.tableState.prevalentWind,
        seatWind = input.player.seatWind,
    )
}
