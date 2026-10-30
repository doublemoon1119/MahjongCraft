package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot

/** 玩家的權威立直宣告；尚未立直時為 null。 */
val MahjongPlayer.riichiDeclaration: RiichiDeclaration? get() = components[RiichiPlayerComponents.DECLARATION]

/** 已向讀取者公開的立直宣告；沒有時為 null。 */
val MahjongPlayerSnapshot.riichiDeclaration: RiichiDeclaration? get() = componentViews[RiichiPlayerComponentViews.DECLARATION]

/**
 * 合併本人牌河與本巡曾放過的牌面，使用標準牌面比較。
 *
 * @return 會造成捨牌或同巡振聽的牌面。
 */
fun MahjongPlayer.riichiFuritenTiles(): Set<Tile> = discardPile.entries.map { it.tile.tile }.toSet() + passedTilesInRound
