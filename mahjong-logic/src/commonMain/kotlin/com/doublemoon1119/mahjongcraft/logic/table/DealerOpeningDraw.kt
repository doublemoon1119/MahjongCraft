package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/**
 * 讓莊家在局首的規則處理之前，先從活牌摸開門的第一張牌，並套用 [module] 的摸牌反應（[MahjongRuleModule.onPlayerDrew]）。
 *
 * 供要求莊家先摸牌、再處理局首事項的規則使用，例如先摸牌再依序補花。莊家的摸牌位已經有牌時不摸。
 *
 * @param module 本局的規則模組。
 * @return 摸牌結果；莊家已有摸牌位的牌或活牌已經摸完時為 null。
 */
fun TableState.drawDealerOpeningTile(module: MahjongRuleModule<*>): PlayerDraw? {
    if (players[dealerIndex].hand.lastDrawn != null) return null
    val draw = drawFromLiveWall(dealerPlayerId) ?: return null
    val dealer = draw.tableState.players[dealerIndex]
    val reacted = dealer.copy(components = module.onPlayerDrew(dealer).applyTo(dealer.components, BuiltInComponentOwners.PLAYER))
    return draw.copy(tableState = draw.tableState.copy(players = draw.tableState.players.map { if (it.id == dealer.id) reacted else it }))
}
