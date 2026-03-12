package com.doublemoon1119.mahjongcraft.application.usecase

import com.doublemoon1119.mahjongcraft.application.ports.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.domain.table.TableState
import kotlinx.coroutines.withContext
import java.util.*

/**
 * 摸牌動作的請求封裝。
 *
 * @property tableState 當前的遊戲桌況。
 * @property playerId 執行摸牌動作的玩家 ID。
 */
data class DrawTileRequest(
    val tableState: TableState,
    val playerId: UUID
)

/**
 * 處理玩家摸牌邏輯的應用層 UseCase。
 *
 * 負責從 [TableState] 內的 [com.doublemoon1119.mahjongcraft.domain.table.TileWall] 取出一張牌，
 * 並將其設置為指定玩家 [com.doublemoon1119.mahjongcraft.domain.table.MahjongPlayer] 手牌中的 [com.doublemoon1119.mahjongcraft.domain.base.Hand.lastDrawn]。
 *
 * @property dispatchers 協程調度器，用於將計算密集型任務切換到背景執行緒。
 */
class DrawTileUseCase(
    private val dispatchers: CoroutineDispatchers
) {

    /**
     * 執行摸牌動作。
     *
     * @param request 摸牌請求參數，包含桌況與玩家資訊。
     * @throws IllegalStateException 當牌山已空、找不到玩家或非該玩家回合時拋出。
     */
    suspend operator fun invoke(request: DrawTileRequest) = withContext(dispatchers.default) {
        val table = request.tableState

        // 尋找目標玩家
        val player = table.players.find { it.id == request.playerId }
            ?: throw IllegalStateException("Player with ID ${request.playerId} not found on this table.")

        // 從牌山摸取一張牌
        val drawnTile = table.tileWall.draw()
            ?: throw IllegalStateException("The tile wall is empty; cannot perform a draw action.")

        // 根據 Hand.kt 的定義，將摸到的牌存入最後摸牌區
        player.hand.lastDrawn = drawnTile
    }
}
