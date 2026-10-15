package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MinecraftMatchAbortReasonIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.OrphanedTablePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlin.uuid.Uuid

/**
 * 依伺服器政策清除缺失桌子的權威狀態、衍生狀態與位置索引。
 *
 * @property store Room、Game 與歷史事實的權威交易儲存。
 * @property memberships 玩家與桌子之間的佔用關係儲存。
 * @property roomSnapshots 等待中房間的觀察者快照儲存。
 * @property gameSnapshots 進行中對局的觀察者快照儲存。
 * @property locations 桌子位置與 revision 的索引。
 * @property configState 提供目前缺失桌子清理政策的伺服器設定狀態。
 * @property removalNotifier 清理前捕捉接收者，清理後安排桌級失效通知。
 */
class OrphanedTableCleanupService(
    private val store: AuthoritativeStateStore,
    private val memberships: PlayerMembershipRepository,
    private val roomSnapshots: RoomSnapshotRepository,
    private val gameSnapshots: GameSnapshotRepository,
    private val locations: TableLocationRegistry,
    private val configState: MinecraftServerConfigState,
    private val removalNotifier: TableRemovalNotifier,
) {
    /** 用於記錄缺失桌子與實際採取的清理政策。 */
    private val logger = mahjongCraftLogger(OrphanedTableCleanupService::class)

    /**
     * 依目前有效設定的 orphan policy 處理已確認缺失的桌子。
     *
     * @param entry 確認缺失時觀察到的位置索引項目；索引已不再是同一個項目時視為過期請求。
     * @return 實際採取的清理結果。
     */
    suspend fun cleanupMissing(entry: TableLocationEntry): OrphanedTableCleanupResult = cleanup(
        entry = entry,
        policy = configState.current.orphanedTablePolicy,
        abortReasonId = MinecraftMatchAbortReasonIds.TABLE_MISSING,
    )

    /**
     * 玩家政策已允許破壞時，移除該桌子的任何 Room／Game。
     *
     * @param entry 玩家破壞時觀察到的位置索引項目；索引已不再是同一個項目時視為過期請求。
     * @return 實際採取的清理結果。
     */
    suspend fun cleanupPlayerBroken(entry: TableLocationEntry): OrphanedTableCleanupResult = cleanup(
        entry = entry,
        policy = OrphanedTablePolicy.REMOVE_ALL,
        abortReasonId = MinecraftMatchAbortReasonIds.TABLE_BROKEN_BY_PLAYER,
    )

    /**
     * 依指定政策原子移除權威狀態，再清除所有衍生資料與位置。
     *
     * 每個步驟都以物件身分比對位置索引：同一桌在請求建立後重新登記時，revision 可能從頭計算而與舊值相同，
     * 只有仍是同一個 [TableLocationEntry] 才代表請求針對的來源仍然存在。
     *
     * @param entry 清理請求建立時觀察到的位置索引項目。
     * @param policy 本次清理採用的 orphan table 政策。
     * @param abortReasonId 移除未完成對局時寫入的中途終止原因 ID。
     * @return 實際採取的清理結果。
     */
    private suspend fun cleanup(
        entry: TableLocationEntry,
        policy: OrphanedTablePolicy,
        abortReasonId: String,
    ): OrphanedTableCleanupResult {
        val tableId = entry.tableId
        if (locations.get(tableId) !== entry) return OrphanedTableCleanupResult.STALE_REQUEST

        val notification = removalNotifier.capture(tableId)
        if (notification != null && notification.ticket.entry !== entry) return OrphanedTableCleanupResult.STALE_REQUEST

        val decision = store.update { state ->
            if (locations.get(tableId) !== entry || (notification != null && !removalNotifier.isCurrent(notification))) {
                return@update AuthoritativeStateUpdate(state, CleanupDecision.stale())
            }
            val room = state.rooms[tableId]
            val game = state.games[tableId]
            when {
                room == null && game == null -> AuthoritativeStateUpdate(state, CleanupDecision.removeLocation())
                policy == OrphanedTablePolicy.KEEP_AND_WARN ->
                    AuthoritativeStateUpdate(state, CleanupDecision.retain())
                game != null && policy == OrphanedTablePolicy.REMOVE_WAITING_ROOM ->
                    AuthoritativeStateUpdate(state, CleanupDecision.retain())
                room != null -> AuthoritativeStateUpdate(
                    state.copy(rooms = state.rooms - tableId),
                    CleanupDecision.removeRoom(room.playerIds.toSet()),
                )
                else -> {
                    val removedGame = checkNotNull(game)
                    AuthoritativeStateUpdate(
                        state = state.copy(games = state.games - tableId),
                        result = CleanupDecision.removeGame(removedGame.tableState.players.map { it.id }.toSet()),
                        historyDraftsByVenueId = if (removedGame.isMatchOver) {
                            emptyMap()
                        } else {
                            mapOf(
                                tableId to listOf(
                                    HistoryEventDraft(
                                        actorPlayerId = null,
                                        fact = HistoryFact.MatchAborted(abortReasonId),
                                    ),
                                ),
                            )
                        },
                    )
                }
            }
        }

        if (decision.result == OrphanedTableCleanupResult.STALE_REQUEST) return decision.result
        if (decision.result == OrphanedTableCleanupResult.RETAINED) {
            logger.warn(
                "Retained orphaned Mahjong table state {} at {} because policy is {}",
                tableId,
                entry.location,
                policy,
            )
            return decision.result
        }

        if (locations.get(tableId) !== entry ||
            (notification != null && !removalNotifier.beginRemoval(notification))
        ) {
            return OrphanedTableCleanupResult.STALE_REQUEST
        }
        var notificationScheduled = false
        try {
            val roomObserverIds = roomSnapshots.getAllObservers(tableId).toSet()
            val gameObserverIds = gameSnapshots.getAllObservers(tableId).toSet()
            val additionalObserverIds = roomObserverIds + gameObserverIds + decision.playerIds

            if (locations.get(tableId) !== entry ||
                (notification != null && !removalNotifier.isCurrent(notification))
            ) {
                return OrphanedTableCleanupResult.STALE_REQUEST
            }

            val isStillValid = {
                val current = store.state.value
                locations.get(tableId) === entry &&
                    tableId !in current.rooms &&
                    tableId !in current.games &&
                    (notification == null || removalNotifier.isCurrent(notification))
            }
            decision.playerIds.forEach { playerId ->
                if (!memberships.releaseIf(playerId, tableId, isStillValid)) return OrphanedTableCleanupResult.STALE_REQUEST
            }
            (roomObserverIds + decision.playerIds).forEach { playerId ->
                if (!roomSnapshots.removeSnapshotIf(tableId, playerId, isStillValid)) return OrphanedTableCleanupResult.STALE_REQUEST
            }
            (gameObserverIds + decision.playerIds).forEach { playerId ->
                if (!gameSnapshots.removeSnapshotIf(tableId, playerId, isStillValid)) return OrphanedTableCleanupResult.STALE_REQUEST
            }
            if (!isStillValid()) return OrphanedTableCleanupResult.STALE_REQUEST
            if (locations.get(tableId) === entry) locations.remove(tableId, entry.revision)
            notification?.let { removalNotifier.completeRemoval(it, additionalObserverIds) }
            notificationScheduled = true
            if (decision.result == OrphanedTableCleanupResult.REMOVED_LOCATION) {
                logger.debug("Removed stale Mahjong table location {} at {}", tableId, entry.location)
            } else {
                logger.warn(
                    "Cleaned orphaned Mahjong table {} at {} with result {}",
                    tableId,
                    entry.location,
                    decision.result,
                )
            }
            return decision.result
        } finally {
            if (!notificationScheduled && notification != null) removalNotifier.cancelRemoval(notification)
        }
    }

    /**
     * 權威狀態交易後需要清除的衍生資料。
     *
     * @property result 對外回報的清理結果。
     * @property playerIds 需要清除 membership 與 observer snapshot 的玩家。
     */
    private data class CleanupDecision(
        val result: OrphanedTableCleanupResult,
        val playerIds: Set<Uuid>,
    ) {
        /** 建立固定清理決策。 */
        companion object {
            /** 清理來源已被取代，不修改狀態或交付通知。 */
            fun stale(): CleanupDecision = CleanupDecision(OrphanedTableCleanupResult.STALE_REQUEST, emptySet())

            /** 保留所有狀態。 */
            fun retain(): CleanupDecision = CleanupDecision(OrphanedTableCleanupResult.RETAINED, emptySet())

            /** 只移除位置。 */
            fun removeLocation(): CleanupDecision = CleanupDecision(OrphanedTableCleanupResult.REMOVED_LOCATION, emptySet())

            /**
             * 移除 Room 與指定玩家的衍生狀態。
             *
             * @param playerIds 需清除 membership 與快照的玩家 UUID。
             */
            fun removeRoom(playerIds: Set<Uuid>): CleanupDecision = CleanupDecision(
                OrphanedTableCleanupResult.REMOVED_ROOM,
                playerIds,
            )

            /**
             * 移除 Game 與指定玩家的衍生狀態。
             *
             * @param playerIds 需清除 membership 與快照的玩家 UUID。
             */
            fun removeGame(playerIds: Set<Uuid>): CleanupDecision = CleanupDecision(
                OrphanedTableCleanupResult.REMOVED_GAME,
                playerIds,
            )
        }
    }
}
