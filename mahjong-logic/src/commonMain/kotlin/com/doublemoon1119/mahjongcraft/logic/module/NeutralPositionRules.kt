package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 不具任何規則知識的規則查詢：和牌價值一律回報無法判斷，宣告沒有效果，沒有不能榮和的牌、沒有依牌張加計的打點，
 * 也沒有宣告聽牌時打出的牌。
 */
object NeutralPositionRules : PositionRules {
    override fun winValue(
        view: PositionView,
        hand: Hand,
        winningTile: Tile,
        isTsumo: Boolean,
        declarations: Set<GameAction.Extension>,
    ): WinValue = WinValue.Unknown

    override fun declarationEffect(view: PositionView, action: GameAction.Extension): DeclarationEffect = DeclarationEffect.NONE
}
