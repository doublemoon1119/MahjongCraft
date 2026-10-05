package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 無法自動恢復的單一玩家跨桌歸屬。 */
data class PlayerMembershipConflict(
    val playerId: Uuid,
    val venueIds: Set<Uuid>,
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
    private val decisionAvailabilityService: GameDecisionAvailabilityService,
) {
    /**
     * 依 [state] 完整取代目前的衍生狀態。
     *
     * 同一玩家若出現在不同場地，會保留各場地權威狀態與 observer snapshot，但暫不建立該玩家的
     * membership，交由玩家首次互動選擇場地。
     */
    suspend fun restore(state: AuthoritativeStateSnapshot): ServerSessionStateRestoreResult {
        val (venueIdsByPlayerId, conflicts) = buildMemberships(state)

        decisionTimerManager.clearAll()
        memberships.replaceAll(venueIdsByPlayerId)
        roomSnapshots.clearAll()
        gameSnapshots.clearAll()

        state.rooms.values.forEach { room ->
            room.humanPlayerIds.forEach { playerId -> roomSnapshots.setSnapshot(playerId, room.toSnapshot(playerId)) }
        }
        state.games.values.forEach { game ->
            game.tableState.players.filterNot { game.isAi(it.id) }.forEach { player ->
                gameSnapshots.setSnapshot(player.id, gameVisibilityPolicy.snapshotFor(game, player.id))
                gameSnapshots.setRoundPreparationSnapshot(
                    gameId = game.id,
                    observerId = player.id,
                    snapshot = gameVisibilityPolicy.roundPreparationSnapshotFor(game, player.id),
                )
            }
            decisionAvailabilityService.reconcile(game.id)
        }
        return ServerSessionStateRestoreResult(conflicts)
    }

    /** 建立無衝突的玩家唯一場地索引，並另外回報跨場地重複玩家。 */
    private fun buildMemberships(
        state: AuthoritativeStateSnapshot,
    ): Pair<Map<Uuid, Uuid>, List<PlayerMembershipConflict>> {
        val venueIdsByPlayerId = mutableMapOf<Uuid, MutableSet<Uuid>>()

        fun add(playerId: Uuid, venueId: Uuid) {
            venueIdsByPlayerId.getOrPut(playerId) { mutableSetOf() }.add(venueId)
        }

        state.rooms.values.forEach { room -> room.humanPlayerIds.forEach { playerId -> add(playerId, room.id) } }
        state.games.values.forEach { game ->
            game.tableState.players.filterNot { game.isAi(it.id) }.forEach { player -> add(player.id, game.id) }
        }
        val conflicts = venueIdsByPlayerId
            .filterValues { it.size > 1 }
            .map { (playerId, venueIds) -> PlayerMembershipConflict(playerId, venueIds.toSet()) }
        val memberships = venueIdsByPlayerId
            .filterValues { it.size == 1 }
            .mapValues { (_, venueIds) -> venueIds.single() }
        return memberships to conflicts
    }
}
