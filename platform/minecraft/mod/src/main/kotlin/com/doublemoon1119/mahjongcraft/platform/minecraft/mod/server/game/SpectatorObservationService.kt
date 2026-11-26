package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SyncGameSnapshotUseCase
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * 讓「不在座位上的玩家」也能正確看到一場正在進行的對局（旁觀）。
 *
 * - 玩家不需要額外操作就會被判斷成旁觀者。用戶端開始看到桌子中央的局況顯示 entity 時，伺服器呼叫 [onStartedObserving]。
 *   這個 entity 是 `MahjongRoundInfoEntity`，每桌一個，對局開始時跟著牌牆出現、結束時消失，見它的
 *   `startSeenByPlayer`／`stopSeenByPlayer`。
 * - 這裡只把他登記為這場對局的觀察者（寫入 [GameSnapshotRepository]）。
 *   [GameEventPublisher.publishToAllObservers] 決定每則對局事件送給哪些人，會把已登記的觀察者一併納入。
 * - 畫面資料由觀察者推送負責，不在這裡送出。
 * - 座位上的玩家一律跳過，他們的快照走右鍵互動桌子的流程。
 *   在場玩家暫時看不到 entity（例如短暫斷線）時，也不會被當成離開而清掉快照。
 *
 * @property scope 執行旁觀者登記與清理的協程範圍。
 * @property gameRepository 讀取對局及玩家座位狀態。
 * @property syncGame 產生並同步符合旁觀政策的快照。
 * @property gameSnapshotRepository 移除離開視野的旁觀者快照。
 */
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
     * 手牌，由 [SpectatingPolicy] 決定）。房間不允許旁觀的話，這裡就什麼都不做，他不會成為這場對局的
     * 事件收件人。
     *
     * @param playerId 開始看到桌面的玩家識別碼。
     * @param tableId 桌面所屬對局識別碼。
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
     * 只清掉旁觀者的快照，讓伺服器不用一直留著「已經沒人在看」的舊資料。在場玩家的快照不會被這裡
     * 動到，理由見類別 KDoc。
     *
     * @param playerId 停止看到桌面的玩家識別碼。
     * @param tableId 桌面所屬對局識別碼。
     */
    fun onStoppedObserving(playerId: Uuid, tableId: Uuid) {
        scope.launch {
            val game = gameRepository.getGame(tableId) ?: return@launch
            if (game.tableState.players.any { it.id == playerId }) return@launch
            gameSnapshotRepository.removeSnapshot(tableId, playerId)
        }
    }
}
