package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 將途中流局記錄至所有玩家的權威動作歷史，並把規則要求公開的手牌（見
 * [MahjongRuleModule.resolveAbortiveDrawRevealedHands]）記進本局已公開手牌。
 *
 * @param reason 本次途中流局原因。
 * @param declarerId 主動宣告者；系統判定的途中流局為 null。
 * @param module 本局的規則模組。
 * @return 記錄流局與公開手牌後的桌況。
 */
internal fun TableState.recordAbortiveDraw(
    reason: ExhaustiveDrawReason,
    declarerId: Uuid?,
    module: MahjongRuleModule<*>,
): TableState {
    val recorded = copy(players = players.map { player -> player.recordAction(GameAction.ExhaustiveDraw(reason)) })
    val revealedHands = module.resolveAbortiveDrawRevealedHands(recorded, declarerId, reason)
    return recorded
        .revealHandsOf(revealedHands.map { it.playerId })
        .copy(revealedHandSettlements = revealedHands)
}

/**
 * 把指定玩家目前的所有立牌記進本局已公開手牌；本局已完成的玩家不公開。
 *
 * @param playerIds 要公開手牌的玩家。
 * @return 加入公開手牌後的桌況。
 */
internal fun TableState.revealHandsOf(playerIds: Collection<Uuid>): TableState {
    val revealedTileIds = players
        .filter { it.id in playerIds && it.id !in finishedPlayerIds }
        .flatMap { player -> player.hand.standingTiles.map { it.id } }
    return if (revealedTileIds.isEmpty()) this else copy(revealedHandTileIds = revealedHandTileIds + revealedTileIds)
}
