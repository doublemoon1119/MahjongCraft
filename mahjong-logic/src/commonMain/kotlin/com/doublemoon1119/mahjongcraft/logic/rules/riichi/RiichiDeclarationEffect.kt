package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationDiscardEffect
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 建立立直宣告的規則效果：記錄立直與一發資格、標記宣告牌、增加一根立直棒的供託並支付一根立直棒。
 *
 * 不負責判定立直是否合法，也不改動手牌、牌河或分數。
 *
 * @param tableState 立直宣告前的權威桌況。
 * @param player 宣告立直的玩家。
 * @param discardResult 立直宣告打出的牌與打牌後手牌。
 * @return 立直宣告的資料變更與費用。
 */
fun riichiDeclarationEffect(
    tableState: TableState,
    player: MahjongPlayer,
    discardResult: Hand.DiscardResult,
): DeclarationDiscardEffect {
    val potPoints = tableState.riichiPotPoints()
    require(potPoints <= Int.MAX_VALUE - RIICHI_STICK_POINTS) { "Riichi pot points overflow" }
    val declaration = RiichiDeclaration(
        kind = if (tableState.isRiichiFirstGoAround(player)) RiichiDeclarationKind.DOUBLE_RIICHI else RiichiDeclarationKind.RIICHI,
        discardTileId = discardResult.tile.id,
        isIppatsu = true,
    )
    return DeclarationDiscardEffect(
        playerComponents = ComponentPatch.basedOn(player.components, values = ComponentSet.EMPTY.with(RiichiPlayerComponents.DECLARATION, declaration)),
        discardComponents = RiichiDiscardComponents.declaration(),
        tableComponents = ComponentPatch.basedOn(tableState.components, values = ComponentSet.EMPTY.with(RiichiTableComponents.POT_POINTS, potPoints + RIICHI_STICK_POINTS)),
        cost = RIICHI_STICK_POINTS,
    )
}
