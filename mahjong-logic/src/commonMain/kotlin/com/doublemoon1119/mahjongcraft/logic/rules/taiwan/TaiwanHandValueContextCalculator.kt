package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.toValue
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueContextCalculator
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 將權威牌況與規則已確認的胡牌情境組成台麻計台輸入。 */
class TaiwanHandValueContextCalculator : HandValueContextCalculator<TaiwanHandValueContext, TaiwanHandValueContextCalculator.Input> {
    /**
     * 正式結算或假設胡牌所需的資料。
     *
     * @property tableState 目前權威桌況。
     * @property player 手牌不含胡牌張的玩家。
     * @property incomingTile 獨立傳入的胡牌張。
     * @property isTsumo 是否為自摸。
     * @property flowers 已補出的花牌面。
     * @property situation 規則流程已確認的胡牌情境，不能由一般快照猜測。
     */
    data class Input(
        val tableState: TableState,
        val player: MahjongPlayer,
        val incomingTile: IdentifiedTile,
        val isTsumo: Boolean,
        val flowers: List<Tile>,
        val situation: TaiwanWinSituation,
    )

    override fun calculate(input: Input): TaiwanHandValueContext {
        require(input.player.hand.allTiles.none { it.id == input.incomingTile.id }) { "Winning tile must be supplied separately from the hand" }
        require(input.tableState.players.any { it.id == input.player.id }) { "Scored player must belong to the table" }
        return TaiwanHandValueContext(
            hand = input.player.hand,
            winningTileValue = input.incomingTile.toValue(),
            isTsumo = input.isTsumo,
            roundWind = input.tableState.prevalentWind,
            seatWind = input.player.seatWind,
            flowers = input.flowers.toList(),
            situation = input.situation,
        )
    }
}
