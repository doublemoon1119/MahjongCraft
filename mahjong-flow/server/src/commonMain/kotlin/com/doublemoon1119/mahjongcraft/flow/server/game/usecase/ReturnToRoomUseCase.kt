package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 對局結束後把桌子從 Game 轉回 Room 的實例化用例。
 *
 * 是 [StartGameUseCase] 的反向操作：在 [AuthoritativeStateStore] 的單次交易內把識別碼從 Game 移回
 * Room，讓玩家能重新使用房間階段的操作（加入／準備／開新局／離開）。呼叫前必須確認
 * [com.doublemoon1119.mahjongcraft.flow.common.game.model.Game.isMatchOver] 已成立——這裡只負責
 * 狀態轉移，不重複判斷對局是否真的結束，那是 [AdvanceRoundUseCase] 的責任。
 *
 * 還原後的 [Room]：`playerIds` 沿用最終桌況的座位順序、`aiPlayerStrategyKeys` 由每位 AI 玩家各自的
 * `aiStrategyKey` 重建、`hostId` 沿用開局時記錄在 [com.doublemoon1119.mahjongcraft.flow.common.game.model.Game.hostId]
 * 的原房主。`readyPlayerIds` 只清空真人玩家（新的一局要所有人重新準備）——AI 玩家維持準備完成，
 * 比照 `AddAiPlayerUseCase` 加入時就直接準備就緒的既有慣例：AI 沒有辦法自己切換準備狀態，若這裡
 * 也把 AI 一併清空，房間會永遠卡在 `Room.canStart` 不成立，沒有人能幫 AI 重新準備。
 *
 * @property authoritativeStateStore Room 與 Game 共用的權威狀態儲存。
 * @property roomSnapshotRepository 房間快照數據倉庫。
 * @property eventPublisher 房間通知服務。
 * @property presentationPublisher 平台呈現層觸發介面，用來清除本局牌牆用牌。
 */
@Factory
class ReturnToRoomUseCase(
    private val authoritativeStateStore: AuthoritativeStateStore,
    private val roomSnapshotRepository: RoomSnapshotRepository,
    @Provided private val eventPublisher: RoomEventPublisher,
    @Provided private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 執行 Game → Room 的狀態轉移。
     *
     * @param gameId 欲轉回房間的對局 Uuid（轉回後同時作為房間的 Uuid）。
     * @return 轉移結果，成功時包含還原後的 [Room]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid): Outcome<Room, GameError> {
        // 1. 驗證對局狀態、移除 Game 與新增 Room 都在同一次 store 交易內完成。
        val outcome = authoritativeStateStore.update { state ->
            val game = state.games[gameId]
            when {
                game == null -> AuthoritativeStateUpdate(state, Outcome.Error(GameError.GameNotFound(gameId)))
                !game.isMatchOver -> AuthoritativeStateUpdate(state, Outcome.Error(GameError.MatchNotOver(gameId)))
                else -> {
                    val tableState = game.tableState
                    val aiPlayerIds = tableState.players.filter { it.isAi }.map { it.id }
                    val newRoom = Room(
                        id = gameId,
                        hostId = game.hostId,
                        gameConfig = GameConfig(ruleConfig = tableState.config, flowConfig = game.flowConfig),
                        playerIds = tableState.players.map { it.id },
                        readyPlayerIds = aiPlayerIds,
                        aiPlayerStrategyKeys = tableState.players
                            .filter { it.isAi }
                            .associate { it.id to checkNotNull(it.aiStrategyKey) },
                    )
                    AuthoritativeStateUpdate(
                        state = state.copy(
                            games = state.games - gameId,
                            rooms = state.rooms + (gameId to newRoom),
                        ),
                        result = Outcome.Success(newRoom),
                    )
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val newRoom = (outcome as Outcome.Success).value

        // 2. 清除本局牌牆用牌；空 structure 觸發呈現層既有的「建空集合後丟棄全部舊牌」語意，不需要
        //    另外定義專用的清除介面方法。莊家座位、王牌集合、擲骰數量在空 structure 下都不影響任何
        //    座標計算，直接傳空／零值即可。
        presentationPublisher.publishWallStructure(gameId, emptyMap(), dealerSeatIndex = 0, deadWallTileIds = emptySet(), diceCount = 0)

        // 3. 為每位玩家同步一份房間快照——這些玩家先前都是 Game 快照的觀察者，不是既有的房間快照
        //    觀察者，不能沿用 CreateRoomUseCase 那種「查詢既有觀察者」的寫法。
        newRoom.playerIds.forEach { playerId ->
            roomSnapshotRepository.setSnapshot(playerId, newRoom.toSnapshot(playerId))
        }

        // 4. 廣播「已回到房間」事件；比照 CreateRoomUseCase 對房主的既有慣例，每位玩家收到一則描述
        //    自己這次狀態變化的通知
        newRoom.playerIds.forEach { playerId ->
            eventPublisher.publishJoin(gameId, playerId, playerId, JoinReason.MatchEnded)
        }

        return Outcome.Success(newRoom)
    }
}
