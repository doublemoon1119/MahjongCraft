package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 開始遊戲的實例化用例。
 *
 * 負責將一個已準備完成的房間轉換為進行中的對局：驗證房間狀態、初始化桌況、
 * 在 [AuthoritativeStateStore] 的單次交易內把識別碼從 Room 移至 Game，並同步快照與廣播開局事件。
 *
 * @property authoritativeStateStore Room 與 Game 共用的權威狀態儲存。
 * @property moduleRegistry 麻將規則模組註冊中心，用於依房間配置解析對應的規則模組。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 */
@Factory
class StartGameUseCase(
    private val authoritativeStateStore: AuthoritativeStateStore,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    @Provided private val eventPublisher: GameEventPublisher,
    @Provided private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 執行開始遊戲邏輯。
     *
     * @param roomId 欲開始的房間 Uuid（開局後將同時作為對局的 Uuid）。
     * @param operatorId 發起開局請求的玩家 Uuid，須為房主。
     * @return 開局結果，成功時包含對局 Uuid（等同 [roomId]），失敗時為 [RoomError]。
     */
    suspend operator fun invoke(roomId: Uuid, operatorId: Uuid): Outcome<Uuid, RoomError> {
        // 1. 驗證房間狀態、初始化桌況、移除 Room 與新增 Game 都在同一次 store 交易內完成。
        val outcome = authoritativeStateStore.update { state ->
            val room = state.rooms[roomId]
            when {
                room == null -> AuthoritativeStateUpdate(state, Outcome.Error(RoomError.RoomNotFound(roomId)))
                room.hostId != operatorId -> AuthoritativeStateUpdate(state, Outcome.Error(RoomError.NotHost(operatorId)))
                !room.canStart -> AuthoritativeStateUpdate(state, Outcome.Error(RoomError.RoomNotReadyToStart(roomId)))
                else -> {
                    val module = moduleRegistry.getModule(room.gameConfig.ruleConfig)
                    val initializationResult = GameInitializer.initialize(
                        id = roomId,
                        playerIds = room.playerIds.toList(),
                        module = module,
                        aiPlayerStrategyKeys = room.aiPlayerStrategyKeys,
                    )
                    val tableState = initializationResult.tableState

                    AuthoritativeStateUpdate(
                        state = state.copy(
                            rooms = state.rooms - roomId,
                            games = state.games + (
                                roomId to Game(
                                    tableState = tableState,
                                    flowConfig = room.gameConfig.flowConfig,
                                )
                                ),
                        ),
                        result = Outcome.Success(initializationResult),
                    )
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val initializationResult = (outcome as Outcome.Success).value
        val tableState = initializationResult.tableState

        // 2. 為每位玩家同步一份對局快照
        tableState.players.forEach { player ->
            snapshotSynchronizer.sync(roomId, player.id)
        }

        // 3. 廣播「對局已開始」事件
        tableState.players.forEach { player ->
            eventPublisher.publish(roomId, player.id, operatorId, GameAction.GameStarted)
        }

        // 4. 觸發平台呈現層：規則不支援開門流程時皆為 null，直接跳過
        initializationResult.diceRoll?.let { presentationPublisher.publishDiceRoll(roomId, it) }
        initializationResult.wallStructure?.let { presentationPublisher.publishWallStructure(roomId, it) }
        presentationPublisher.publishGameStarted(roomId, tableState.players.map { it.id })

        return Outcome.Success(roomId)
    }
}
