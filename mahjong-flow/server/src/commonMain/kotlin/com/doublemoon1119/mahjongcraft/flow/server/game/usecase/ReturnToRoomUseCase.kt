package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import kotlin.uuid.Uuid

/**
 * 對局結束後把場地從 Game 轉回 Room 的實例化用例。
 *
 * 是 [StartGameUseCase] 的反向操作：在 [AuthoritativeStateStore] 的單次交易內把識別碼從 Game 移回
 * Room，讓玩家能重新使用房間階段的操作（加入／準備／開新局／離開）。呼叫前必須確認[Game.isMatchOver] 已成立——
 * 這裡只負責狀態轉移，不重複判斷對局是否真的結束，那是 [AdvanceRoundUseCase] 的責任。
 *
 * 還原後的 [Room]：`playerIds` 沿用最終桌況的座位順序、`aiPlayerStrategyKeys` 沿用
 * [Game.aiPlayerStrategyKeys]、`hostId` 沿用開局時記錄在 [Game.hostId] 的原房主。
 * `readyPlayerIds` 只清空真人玩家（新的一局要所有人重新準備）——AI 玩家維持準備完成，
 * 比照 `AddAiPlayerUseCase` 加入時就直接準備就緒的既有慣例：AI 沒有辦法自己切換準備狀態，若這裡
 * 也把 AI 一併清空，房間會永遠卡在 `Room.canStart` 不成立，沒有人能幫 AI 重新準備。
 *
 * @property authoritativeStateStore Room 與 Game 共用的權威狀態儲存。
 * @property roomSnapshotRepository 房間快照數據倉庫。
 * @property eventPublisher 房間通知服務。
 * @property presentationPublisher 平台呈現層觸發介面，用來清除本局牌牆用牌。
 */
class ReturnToRoomUseCase(
    private val authoritativeStateStore: AuthoritativeStateStore,
    private val roomSnapshotRepository: RoomSnapshotRepository,
    private val eventPublisher: RoomEventPublisher,
    private val presentationPublisher: GamePresentationPublisher,
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
                    val newRoom = GameRoomRestoration.roomFor(game)
                    AuthoritativeStateUpdate(
                        state = state.copy(
                            games = state.games - gameId,
                            rooms = state.rooms + (gameId to newRoom),
                        ),
                        result = Outcome.Success(newRoom),
                        historyDraftsByVenueId = mapOf(
                            gameId to listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom)),
                        ),
                    )
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val newRoom = (outcome as Outcome.Success).value

        GameRoomRestoration.publish(
            room = newRoom,
            reason = JoinReason.MatchEnded,
            snapshots = roomSnapshotRepository,
            events = eventPublisher,
            presentation = presentationPublisher,
        )

        return Outcome.Success(newRoom)
    }
}
