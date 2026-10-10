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
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 缺失麻將桌的清理結果。 */
enum class OrphanedTableCleanupResult {
    /** 延遲請求已過期，不變更任何狀態。 */
    STALE_REQUEST,

    /** 政策要求保留目前 Room／Game 與位置索引。 */
    RETAINED,

    /** 已移除等待中的 Room 與其衍生狀態。 */
    REMOVED_ROOM,

    /** 已移除進行中的 Game 與其衍生狀態。 */
    REMOVED_GAME,

    /** 沒有權威狀態，只移除過期位置索引。 */
    REMOVED_LOCATION,
}

/**
 * 依伺服器政策清除缺失桌子的權威狀態、衍生狀態與位置索引。
 *
 * @property store Room、Game 與歷史事實的權威交易儲存。
 * @property memberships 玩家與桌子之間的佔用關係儲存。
 * @property roomSnapshots 等待中房間的觀察者快照儲存。
 * @property gameSnapshots 進行中對局的觀察者快照儲存。
 * @property locations 桌子位置與 revision 的索引。
 * @property configState 提供目前缺失桌子清理政策的伺服器設定狀態。
 */
@Single
class OrphanedTableCleanupService(
    private val store: AuthoritativeStateStore,
    private val memberships: PlayerMembershipRepository,
    private val roomSnapshots: RoomSnapshotRepository,
    private val gameSnapshots: GameSnapshotRepository,
    private val locations: TableLocationRegistry,
    private val configState: MinecraftServerConfigState,
) {
    /** 用於記錄缺失桌子與實際採取的清理政策。 */
    private val logger = mahjongCraftLogger(OrphanedTableCleanupService::class)

    /** 依目前有效設定的 orphan policy 處理已確認缺失的桌子。
     *
     * @param tableId 已確認缺失的桌子 UUID。
     * @param expectedRevision 呼叫端觀察到的位置索引 revision。
     * @return 實際採取的清理結果。
     */
    suspend fun cleanupMissing(tableId: Uuid, expectedRevision: Long): OrphanedTableCleanupResult = cleanup(
        tableId = tableId,
        expectedRevision = expectedRevision,
        policy = configState.current.orphanedTablePolicy,
        abortReasonId = MinecraftMatchAbortReasonIds.TABLE_MISSING,
    )

    /** 玩家政策已允許破壞時，移除該桌子的任何 Room／Game。
     *
     * @param tableId 玩家破壞的桌子 UUID。
     * @param expectedRevision 呼叫端觀察到的位置索引 revision。
     * @return 實際採取的清理結果。
     */
    suspend fun cleanupPlayerBroken(tableId: Uuid, expectedRevision: Long): OrphanedTableCleanupResult = cleanup(
        tableId = tableId,
        expectedRevision = expectedRevision,
        policy = OrphanedTablePolicy.REMOVE_ALL,
        abortReasonId = MinecraftMatchAbortReasonIds.TABLE_BROKEN_BY_PLAYER,
    )

    /** 依指定政策原子移除權威狀態，再清除所有衍生資料與位置。
     *
     * @param tableId 要清理的桌子 UUID。
     * @param expectedRevision 清理請求建立時的位置索引 revision。
     * @param policy 本次清理採用的 orphan table 政策。
     * @param abortReasonId 移除未完成對局時寫入的中途終止原因 ID。
     * @return 實際採取的清理結果。
     */
    private suspend fun cleanup(
        tableId: Uuid,
        expectedRevision: Long,
        policy: OrphanedTablePolicy,
        abortReasonId: String,
    ): OrphanedTableCleanupResult {
        val entry = locations.get(tableId)
        if (entry == null || entry.revision != expectedRevision) return OrphanedTableCleanupResult.STALE_REQUEST

        val decision = store.update { state ->
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

        if (decision.result == OrphanedTableCleanupResult.RETAINED) {
            logger.warn(
                "Retained orphaned Mahjong table state {} at {} because policy is {}",
                tableId,
                entry.location,
                policy,
            )
            return decision.result
        }

        decision.playerIds.forEach { playerId -> memberships.release(playerId, tableId) }
        (roomSnapshots.getAllObservers(tableId) + decision.playerIds).forEach { playerId ->
            roomSnapshots.removeSnapshot(tableId, playerId)
        }
        (gameSnapshots.getAllObservers(tableId) + decision.playerIds).forEach { playerId ->
            gameSnapshots.removeSnapshot(tableId, playerId)
        }
        locations.remove(tableId, expectedRevision)
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
    }

    /** 權威狀態交易後需要清除的衍生資料。
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
            /** 保留所有狀態。 */
            fun retain(): CleanupDecision = CleanupDecision(OrphanedTableCleanupResult.RETAINED, emptySet())

            /** 只移除位置。 */
            fun removeLocation(): CleanupDecision = CleanupDecision(OrphanedTableCleanupResult.REMOVED_LOCATION, emptySet())

            /** 移除 Room 與指定玩家的衍生狀態。 */
            fun removeRoom(playerIds: Set<Uuid>): CleanupDecision = CleanupDecision(
                OrphanedTableCleanupResult.REMOVED_ROOM,
                playerIds,
            )

            /** 移除 Game 與指定玩家的衍生狀態。 */
            fun removeGame(playerIds: Set<Uuid>): CleanupDecision = CleanupDecision(
                OrphanedTableCleanupResult.REMOVED_GAME,
                playerIds,
            )
        }
    }
}
