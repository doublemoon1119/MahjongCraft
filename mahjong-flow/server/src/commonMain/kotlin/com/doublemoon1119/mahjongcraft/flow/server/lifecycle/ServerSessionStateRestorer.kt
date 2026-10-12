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

/**
 * 從已載入的權威狀態重建單次伺服器工作階段使用的衍生索引與觀察者快照。
 *
 * @property roomSnapshots 房間觀察者快照倉庫。
 * @property gameSnapshots 對局觀察者快照倉庫。
 * @property memberships 玩家唯一場地歸屬倉庫。
 * @property gameVisibilityPolicy 對局快照可見範圍政策。
 * @property decisionTimerManager 對局決策計時器管理器。
 * @property decisionAvailabilityService 對局決策可用性同步服務。
 */
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
     * 同一玩家若出現在不同場地，會保留各場地權威狀態與觀察者快照，但暫不建立該玩家的
     * 場地歸屬，交由玩家首次互動選擇場地。
     *
     * @param state 已載入的權威狀態快照。
     * @return 重建後發現的跨場地玩家歸屬衝突。
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

    /**
     * 建立無衝突的玩家唯一場地索引，並另外回報跨場地重複玩家。
     *
     * @param state 欲檢查的權威狀態快照。
     * @return 無衝突玩家索引及跨場地歸屬衝突清單。
     */
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
