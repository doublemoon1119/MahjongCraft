package com.doublemoon1119.mahjongcraft.usecase

import com.doublemoon1119.mahjongcraft.model.table.TableState
import java.util.*

/**
 * 處理玩家摸牌邏輯的領域層 UseCase。
 *
 * 負責從 [TableState] 內的 [com.doublemoon1119.mahjongcraft.model.table.TileWall] 取出一張牌，
 * 並將其設置為指定玩家 [com.doublemoon1119.mahjongcraft.model.table.MahjongPlayer] 手牌中的 [com.doublemoon1119.mahjongcraft.model.base.Hand.lastDrawn]。
 */
class DrawTileUseCase {

    /**
     * 執行摸牌動作。
     *
     * @param tableState 當前的對局桌況。
     * @param playerId 執行摸牌動作的玩家唯一識別碼。
     * @throws IllegalStateException 當在桌上找不到該玩家、或是牌山已空（荒牌）時拋出。
     */
    operator fun invoke(tableState: TableState, playerId: UUID) {
        // 尋找目標玩家
        val player = tableState.players.find { it.id == playerId }
            ?: throw IllegalStateException("Player with ID $playerId not found on the current table.")

        // 從牌山摸取一張牌
        val drawnTile = tableState.tileWall.draw()
            ?: throw IllegalStateException("The tile wall is empty; cannot perform a draw action.")

        // 根據 Hand.kt 的定義，將摸到的牌存入最後摸牌區
        player.hand.lastDrawn = drawnTile
    }
}