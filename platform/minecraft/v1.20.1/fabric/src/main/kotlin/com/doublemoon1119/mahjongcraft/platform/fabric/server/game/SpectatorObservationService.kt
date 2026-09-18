package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SyncGameSnapshotUseCase
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
 * 這裡只負責把他登記為這場對局的觀察者（寫入 [GameSnapshotRepository]），讓
 * [GameEventPublisher.publishToTable] 之後的每則對局事件也送給他——那裡才是決定「這則通知要送給
 * 哪些人」的地方，會把已登記的觀察者一併納入。畫面資料本身由觀察者推送負責，不在這裡送出。
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
    private val gameSnapshotRepository: GameSnapshotRepository,
) {
    /**
     * 玩家開始看到 [tableId] 這桌的局況顯示時呼叫。
     *
     * 如果這位玩家其實是在場的四人之一，這裡不做任何事（交給右鍵互動桌子的既有流程）。
     * 如果他是外人，且房間設定允許旁觀，就依旁觀規則產生一份過濾過的快照登記起來（例如要不要看得到
     * 手牌，由 [SpectatingPolicy] 決定）；房間不允許旁觀的話，這裡就什麼都不做，他不會成為這場對局的
     * 事件收件人。
     */
    fun onStartedObserving(playerId: Uuid, tableId: Uuid) {
        scope.launch {
            val game = gameRepository.getGame(tableId) ?: return@launch
            if (game.tableState.players.any { it.id == playerId }) return@launch
            if (game.flowConfig.spectatingPolicy != SpectatingPolicy.ENABLED) return@launch
            syncGame(tableId, playerId)
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
