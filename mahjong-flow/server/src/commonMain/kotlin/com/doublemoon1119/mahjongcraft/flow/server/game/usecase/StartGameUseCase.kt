package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 開始遊戲的實例化用例。
 *
 * 負責將一個已準備完成的房間轉換為進行中的對局：驗證房間狀態、初始化桌況、
 * 把識別碼從 [RoomRepository] 「搬家」到 [GameRepository]，並同步快照與廣播開局事件。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於依房間配置解析對應的規則模組。
 * @property gameSnapshotRepository 對局快照數據倉庫。
 * @property eventPublisher 對局通知服務。
 */
@Factory
class StartGameUseCase(
    private val roomRepository: RoomRepository,
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val gameSnapshotRepository: GameSnapshotRepository,
    @Provided private val eventPublisher: GameEventPublisher
) {
    /**
     * 執行開始遊戲邏輯。
     *
     * @param roomId 欲開始的房間 Uuid（開局後將同時作為對局的 Uuid）。
     * @param operatorId 發起開局請求的玩家 Uuid，須為房主。
     * @return 開局結果，成功時包含對局 Uuid（等同 [roomId]），失敗時為 [RoomError]。
     */
    suspend operator fun invoke(roomId: Uuid, operatorId: Uuid): Outcome<Uuid, RoomError> {
        // 1. 驗證房間狀態、初始化桌況、寫入 GameRepository、移除 Room，這四步都包在同一次
        //    roomRepository.update() 裡面做完，中途不會被其他開局請求插隊，避免同一房間被
        //    重複開局兩次。
        //
        //    這裡的呼叫順序是「先鎖 Room，再呼叫 Game」。之後如果有其他 use case
        //    需要同時碰兩個 repository，也要照這個順序寫，不能反過來「先鎖 Game 再呼叫 Room」——
        //    否則兩個 use case 同時執行時，可能各自鎖住一邊、卡住等對方，永遠等不到（死鎖）。
        val outcome = roomRepository.update(roomId) { room ->
            when {
                room == null -> room to Outcome.Error(RoomError.RoomNotFound(roomId))
                room.hostId != operatorId -> room to Outcome.Error(RoomError.NotHost(operatorId))
                !room.canStart -> room to Outcome.Error(RoomError.RoomNotReadyToStart(roomId))
                else -> {
                    val module = moduleRegistry.getModule(room.config)
                    val tableState = GameInitializer.initialize(roomId, room.playerIds.toList(), module)

                    // 先寫入 GameRepository，確認成功後才回傳 null 移除 Room，
                    // 避免「Room 已刪除但 Game 未寫入」的資料遺失窗口。
                    gameRepository.setTableState(tableState)

                    null to Outcome.Success(tableState)
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val tableState = (outcome as Outcome.Success).value

        // 2. 為每位玩家同步一份對局快照
        tableState.players.forEach { player ->
            gameSnapshotRepository.setSnapshot(player.id, tableState.toSnapshot(player.id))
        }

        // 3. 廣播「對局已開始」事件
        tableState.players.forEach { player ->
            eventPublisher.publish(roomId, player.id, operatorId, GameAction.GameStarted)
        }

        return Outcome.Success(roomId)
    }
}
