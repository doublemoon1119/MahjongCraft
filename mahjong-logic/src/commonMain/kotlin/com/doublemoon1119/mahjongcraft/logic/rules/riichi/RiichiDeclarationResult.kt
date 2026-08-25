package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 套用立直後交回 flow 的日麻規則狀態更新。 */
data class RiichiDeclarationResult(
    val player: MahjongPlayer,
    val dynamicRuleState: RiichiDynamicState,
)

/** 套用日本麻將立直宣告的規則專屬狀態變化。 */
fun applyRiichiDeclaration(
    tableState: TableState,
    player: MahjongPlayer,
    discardResult: Hand.DiscardResult,
): RiichiDeclarationResult? {
    val riichiState = player.playerRuleState as? RiichiPlayerState ?: return null
    val riichiDiscardPile = player.discardPile as? RiichiDiscardPile ?: return null
    val riichiDynamicState = tableState.dynamicRuleState as? RiichiDynamicState ?: return null
    val isDoubleRiichi = tableState.isFirstGoAround(player)
    val updatedPlayerRuleState = riichiState.copy(
        riichiTile = if (isDoubleRiichi) null else discardResult.tile,
        doubleRiichiTile = if (isDoubleRiichi) discardResult.tile else null,
        isIppatsu = true,
    )
    val updatedPlayer = player.copy(
        hand = discardResult.hand,
        discardPile = riichiDiscardPile.discard(RiichiDiscardEntry(discardResult.tile, isRiichi = true)),
        score = player.score - 1000,
        playerRuleState = updatedPlayerRuleState,
    )
    return RiichiDeclarationResult(
        player = updatedPlayer,
        dynamicRuleState = riichiDynamicState.copy(riichiStickCount = riichiDynamicState.riichiStickCount + 1),
    )
}
