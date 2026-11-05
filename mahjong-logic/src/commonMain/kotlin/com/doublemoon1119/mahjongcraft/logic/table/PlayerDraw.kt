package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import kotlin.uuid.Uuid

/**
 * 玩家摸一張牌的結果。
 *
 * @property tableState 摸牌後的桌況。
 * @property tile 摸到的牌。
 */
data class PlayerDraw(
    val tableState: TableState,
    val tile: IdentifiedTile,
)

/**
 * 讓 [playerId] 從活牌最前端摸一張牌到摸牌位並記錄摸牌，清除他本巡放過的牌，階段改為等行動；不套用摸牌後的規則反應。
 *
 * @param playerId 摸牌的玩家，必須是目前玩家且在等摸牌階段。
 * @return 摸牌結果；活牌已經摸完時為 null。
 * @throws IllegalArgumentException 不是輪到這位玩家摸牌時拋出。
 */
fun TableState.drawFromLiveWall(playerId: Uuid): PlayerDraw? {
    require(currentPlayer.id == playerId && turnPhase == TurnPhase.AwaitingDraw) { "Drawing player must be the current player awaiting a draw" }
    val drawer = currentPlayer
    val (tile, wall) = tileWall.draw()
    if (tile == null) return null
    val remainingWallTileIds = (wall.getAllTiles() + reservedWallTiles).mapTo(mutableSetOf()) { it.id }
    val drawn = drawer.copy(hand = drawer.hand.copy(lastDrawn = tile)).clearPassedTiles().recordAction(GameAction.Draw)
    return PlayerDraw(
        tableState = copy(
            players = players.map { if (it.id == playerId) drawn else it },
            tileWall = wall,
            physicalWallLayout = physicalWallLayout?.retainOnly(remainingWallTileIds),
            turnPhase = TurnPhase.AwaitingAction,
        ),
        tile = tile,
    )
}
