package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortEffects
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate

/**
 * 對局無法安全繼續時，原子地中止本場並還原房間，不改分數或補造正常終局。
 *
 * 僅在權威對局仍等於失敗時的快照時提交。重複呼叫或舊背景結果不能移除後來的新場次。
 * 權威資料先完成轉移，再處理呈現與通知；不移除場地或釋放仍屬於該房間的參與關係。
 *
 * @property store 房間與對局共用的權威交易來源。
 * @property gameSnapshots 清除舊場次的觀察者快照。
 * @property roomSnapshots 寫入恢復後的房間快照。
 * @property roomEvents 通知參與者已返回房間。
 * @property presentation 清除舊牌張與呈現。
 * @property availability 清除已不存在對局的決策計時。
 * @property winHandoff 丟棄尚未交付的和牌呈現。
 * @property effects 執行環境的額外清理、通知與診斷。
 */
class AbortGameUseCase(
    private val store: AuthoritativeStateStore,
    private val gameSnapshots: GameSnapshotRepository,
    private val roomSnapshots: RoomSnapshotRepository,
    private val roomEvents: RoomEventPublisher,
    private val presentation: GamePresentationPublisher,
    private val availability: GameDecisionAvailabilityService,
    private val winHandoff: WinPresentationHandoff,
    private val effects: GameAbortEffects,
) : GameAbortHandler {
    /**
     * 中止仍符合 [expectedGame] 的場次，只記錄一次提前終止。
     *
     * @param expectedGame 發現錯誤時的權威對局。
     * @param reasonId 終止原因識別碼。
     * @param diagnostic 僅提供給管理診斷的說明。
     * @return 本次確實提交中止時為 true；已移除、已正常結束或狀態過期時為 false。
     */
    override suspend fun abort(expectedGame: Game, reasonId: String, diagnostic: String): Boolean {
        val gameId = expectedGame.id
        val observers = gameSnapshots.getAllObservers(gameId) + expectedGame.roomPlayerIds
        val room = store.update { state ->
            val current = state.games[gameId]
            if (current == null || current != expectedGame || current.isMatchOver) {
                return@update AuthoritativeStateUpdate(state, null)
            }
            val restored = GameRoomRestoration.roomFor(current)
            AuthoritativeStateUpdate(
                state = state.copy(games = state.games - gameId, rooms = state.rooms + (gameId to restored)),
                result = restored,
                historyDraftsByVenueId = mapOf(
                    gameId to listOf(HistoryEventDraft(actorPlayerId = null, fact = HistoryFact.MatchAborted(reasonId))),
                ),
            )
        } ?: return false

        winHandoff.discard(gameId)
        availability.reconcile(gameId)
        try {
            effects.apply(
                game = expectedGame,
                room = room,
                observerIds = observers,
                reasonId = reasonId,
                diagnostic = diagnostic,
            )
        } finally {
            // 外部清理失敗不能阻止核心快照回房；原始例外仍向呼叫端傳遞。
            val isCurrent = { store.state.value.rooms[gameId] === room && gameId !in store.state.value.games }
            observers.forEach { observerId -> gameSnapshots.removeSnapshotIf(gameId, observerId, isCurrent) }
            if (isCurrent()) {
                GameRoomRestoration.publish(
                    room = room,
                    reason = JoinReason.MatchAborted,
                    snapshots = roomSnapshots,
                    events = roomEvents,
                    presentation = presentation,
                )
            }
        }
        return true
    }
}
