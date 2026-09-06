package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SyncGameSnapshotUseCase
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.GameSnapshotSender
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 讓「不在座位上的玩家」也能正確看到一場正在進行的對局（旁觀）。
 *
 * 玩家不需要做任何額外操作就能被判斷成旁觀者——只要他的用戶端開始看到桌子中央的局況顯示 entity
 * （`MahjongRoundInfoEntity`，每桌固定一個，對局一開始就跟著牌牆出現、對局結束就消失，見它的
 * `onStartedTrackingBy`／`onStoppedTrackingBy`），伺服器就會呼叫這裡的 [onStartedObserving]。
 *
 * 這裡只負責「第一次補一份快照給他」；之後牌局有任何變化要不要繼續推播給他，交給
 * [com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher.publishToTable]——
 * 那裡才是真正每次動作後決定「這則通知要送給哪些人」的地方，一樣會把旁觀者（[GameSnapshotRepository]
 * 已登記的觀察者）納入。只呼叫 `syncAll`（把最新快照寫回 repository）並不夠：那一步只更新了
 * repository 裡「存了什麼」，真正把資料送到玩家用戶端手上的是另外那個廣播步驟，如果那步驟只找
 * 在場座位上的玩家、沒去問 repository 裡登記了哪些觀察者，旁觀者一樣收不到——這正是這個機制第一版
 * 的實際 bug（旁觀者能看到「當下那一刻」的畫面，但後續摸牌/捨牌永遠停在 unknown，因為新事件從未
 * 送達）。
 *
 * 在場玩家（座位上的四人）完全不受這裡影響——他們的快照走既有的右鍵互動桌子流程，這裡看到在場
 * 玩家一律直接跳過，避免搶著處理同一件事，也避免不小心把在場玩家暫時看不到 entity（例如短暫斷線）
 * 誤判成「這個人不玩了、把他的快照清掉」。
 */
@Single
class SpectatorObservationService(
    private val scope: AppCoroutineScope,
    private val gameRepository: GameRepository,
    private val syncGame: SyncGameSnapshotUseCase,
    private val gameSnapshotSender: GameSnapshotSender,
    private val gameSnapshotRepository: GameSnapshotRepository,
) {
    /**
     * 玩家開始看到 [tableId] 這桌的局況顯示時呼叫。
     *
     * 如果這位玩家其實是在場的四人之一，這裡不做任何事（交給右鍵互動桌子的既有流程）。
     * 如果他是外人，且房間設定允許旁觀，就補送一份依旁觀規則過濾過的快照給他（例如要不要看得到
     * 手牌，由 [SpectatingPolicy] 決定）；房間不允許旁觀的話，這裡就什麼都不做，他會繼續看到預設
     * 的「不知道是什麼牌」外觀。
     */
    fun onStartedObserving(playerId: Uuid, tableId: Uuid) {
        scope.launch {
            val game = gameRepository.getGame(tableId) ?: return@launch
            if (game.tableState.players.any { it.id == playerId }) return@launch
            if (game.flowConfig.spectatingPolicy != SpectatingPolicy.ENABLED) return@launch
            syncGame(tableId, playerId)
            gameSnapshotSender.send(tableId, playerId)
        }
    }

    /**
     * 玩家不再看到 [tableId] 這桌的局況顯示時呼叫（走遠、離線，或這場對局已經結束）。
     *
     * 只清掉旁觀者的快照，讓伺服器不用一直留著「已經沒人在看」的舊資料；在場玩家的快照不會被這裡
     * 動到，理由見類別 KDoc。
     */
    fun onStoppedObserving(playerId: Uuid, tableId: Uuid) {
        scope.launch {
            val game = gameRepository.getGame(tableId) ?: return@launch
            if (game.tableState.players.any { it.id == playerId }) return@launch
            gameSnapshotRepository.removeSnapshot(tableId, playerId)
        }
    }
}
