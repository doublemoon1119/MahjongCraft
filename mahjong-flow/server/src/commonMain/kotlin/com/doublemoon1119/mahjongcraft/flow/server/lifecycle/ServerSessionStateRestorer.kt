package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 無法自動恢復的單一玩家跨桌歸屬。 */
data class PlayerMembershipConflict(
    val playerId: Uuid,
    val tableIds: Set<Uuid>,
)

/** server session 衍生狀態恢復結果。 */
data class ServerSessionStateRestoreResult(
    val membershipConflicts: List<PlayerMembershipConflict>,
)

/** 從已載入的權威狀態重建單次 server session 使用的衍生索引與 observer snapshot。 */
@Single
class ServerSessionStateRestorer(
    private val roomSnapshots: RoomSnapshotRepository,
    private val gameSnapshots: GameSnapshotRepository,
    private val memberships: PlayerMembershipRepository,
    private val gameVisibilityPolicy: GameVisibilityPolicy,
    private val decisionTimerManager: GameDecisionTimerManager,
) {
    /**
     * 依 [state] 完整取代目前的衍生狀態。
     *
     * 同一玩家若出現在不同桌子，會保留各桌權威狀態與 observer snapshot，但暫不建立該玩家的
     * membership，交由玩家首次互動選擇桌子。
     */
    suspend fun restore(state: AuthoritativeStateSnapshot): ServerSessionStateRestoreResult {
        val (tableIdsByPlayerId, conflicts) = buildMemberships(state)

        decisionTimerManager.clearAll()
        memberships.replaceAll(tableIdsByPlayerId)
        roomSnapshots.clearAll()
        gameSnapshots.clearAll()

        state.rooms.values.forEach { room ->
            room.humanPlayerIds.forEach { playerId -> roomSnapshots.setSnapshot(playerId, room.toSnapshot(playerId)) }
        }
        state.games.values.forEach { game ->
            game.tableState.players.filter { it.aiStrategyKey == null }.forEach { player ->
                gameSnapshots.setSnapshot(player.id, gameVisibilityPolicy.snapshotFor(game, player.id))
                gameSnapshots.setRoundPreparationSnapshot(
                    gameId = game.id,
                    observerId = player.id,
                    snapshot = gameVisibilityPolicy.roundPreparationSnapshotFor(game, player.id),
                )
            }
            decisionTimerManager.reconcile(game.id)
        }
        return ServerSessionStateRestoreResult(conflicts)
    }

    /** 建立無衝突的玩家唯一桌子索引，並另外回報跨桌重複玩家。 */
    private fun buildMemberships(
        state: AuthoritativeStateSnapshot,
    ): Pair<Map<Uuid, Uuid>, List<PlayerMembershipConflict>> {
        val tableIdsByPlayerId = mutableMapOf<Uuid, MutableSet<Uuid>>()

        fun add(playerId: Uuid, tableId: Uuid) {
            tableIdsByPlayerId.getOrPut(playerId) { mutableSetOf() }.add(tableId)
        }

        state.rooms.values.forEach { room -> room.humanPlayerIds.forEach { playerId -> add(playerId, room.id) } }
        state.games.values.forEach { game ->
            game.tableState.players.filter { it.aiStrategyKey == null }.forEach { player -> add(player.id, game.id) }
        }
        val conflicts = tableIdsByPlayerId
            .filterValues { it.size > 1 }
            .map { (playerId, tableIds) -> PlayerMembershipConflict(playerId, tableIds.toSet()) }
        val memberships = tableIdsByPlayerId
            .filterValues { it.size == 1 }
            .mapValues { (_, tableIds) -> tableIds.single() }
        return memberships to conflicts
    }
}
