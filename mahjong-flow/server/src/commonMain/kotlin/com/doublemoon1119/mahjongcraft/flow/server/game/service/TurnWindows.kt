package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 是否有任何玩家正在決定要不要回應一張捨牌或一次搶和；決定完成前，沒有人能進行自己回合的操作或摸牌。
 */
val TableState.hasPendingReaction: Boolean
    get() = pendingReaction != null || pendingRobbingReaction != null

/**
 * [playerId] 現在能否摸牌：輪到他、手上還沒有剛摸到的牌、不是剛鳴牌後必須直接捨牌，且沒有玩家正在決定是否回應。
 *
 * 自動摸牌的呼叫端與摸牌用例共用此判斷，提供摸牌與接受摸牌的條件一致。
 *
 * @param playerId 要判斷的玩家。
 * @return 現在可以摸牌時為 true。
 */
fun TableState.isDrawWindowOpenFor(playerId: Uuid): Boolean {
    val current = currentPlayer
    return current.id == playerId && current.hand.lastDrawn == null && !current.justClaimedMeld && !hasPendingReaction
}

/**
 * [playerId] 現在是否處於自己回合的決策：輪到他、已摸牌或剛鳴牌，且沒有玩家正在決定是否回應。
 *
 * 捨牌、自摸、宣告與槓等自己回合的用例，與解析目前誰取得操作權的 [PlayerActionContextResolver] 共用此判斷。
 * 各用例另外檢查該動作自己的條件，例如自摸需要剛摸到的牌。
 *
 * @param playerId 要判斷的玩家。
 * @return 現在處於該玩家自己回合的決策時為 true。
 */
fun TableState.isOwnTurnWindowOpenFor(playerId: Uuid): Boolean {
    val current = currentPlayer
    return current.id == playerId && (current.hand.lastDrawn != null || current.justClaimedMeld) && !hasPendingReaction
}
