package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 套用立直後交回呼叫端的日麻規則狀態更新。
 *
 * @property player 套用立直宣告與捨牌後的玩家狀態。
 * @property dynamicRuleState 立直棒數量更新後的規則動態狀態。
 */
data class RiichiDeclarationResult(
    val player: MahjongPlayer,
    val dynamicRuleState: RiichiDynamicState,
)

/**
 * 建立立直宣告後的玩家與規則動態狀態。
 *
 * 此函式依傳入的捨牌結果更新手牌、牌河、玩家分數、立直狀態與場上立直棒數；不負責判定立直是否合法。
 *
 * @param tableState 立直宣告前的權威桌況。
 * @param player 宣告立直的玩家。
 * @param discardResult 已完成立直捨牌後的手牌與牌張。
 * @return 更新後的玩家與日麻動態狀態；玩家規則狀態、牌河或桌況動態狀態不是日麻型別時為 null。
 */
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
        score = player.score - RIICHI_STICK_POINTS,
        playerRuleState = updatedPlayerRuleState,
    )
    return RiichiDeclarationResult(
        player = updatedPlayer,
        dynamicRuleState = riichiDynamicState.copy(riichiStickCount = riichiDynamicState.riichiStickCount + 1),
    )
}
