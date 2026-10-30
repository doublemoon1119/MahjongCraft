package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlinx.coroutines.CancellationException
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
 * @property moduleRegistry 辨識投影失敗的規則，供診斷使用。
 * @property failurePolicy 決定哪些擴充錯誤可隔離，取消及致命錯誤仍往外傳。
 */
class ServerSessionStateRestorer(
    private val roomSnapshots: RoomSnapshotRepository,
    private val gameSnapshots: GameSnapshotRepository,
    private val memberships: PlayerMembershipRepository,
    private val gameVisibilityPolicy: GameVisibilityPolicy,
    private val decisionTimerManager: GameDecisionTimerManager,
    private val decisionAvailabilityService: GameDecisionAvailabilityService,
    private val moduleRegistry: MahjongModuleRegistry,
    private val failurePolicy: ExtensionFailurePolicy,
) {
    /**
     * 依 [state] 完整取代目前的衍生狀態。
     *
     * 同一玩家若出現在不同場地，會保留各場地權威狀態與觀察者快照，但暫不建立該玩家的
     * 場地歸屬，交由玩家首次互動選擇場地。
     *
     * @param state 已載入的權威狀態快照。
     * @return 跨場地玩家歸屬衝突及已隔離的對局投影失敗。
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
        val snapshotFailures = mutableListOf<GameSnapshotRestoreFailure>()
        state.games.values.forEach { game ->
            val humanIds = game.tableState.players.filterNot { game.isAi(it.id) }.map { it.id }
            var usedConservativeSnapshots = false
            val snapshots = try {
                humanIds.associateWith { observerId ->
                    RestoredObserverSnapshot(
                        table = gameVisibilityPolicy.snapshotFor(game, observerId),
                        decision = gameVisibilityPolicy.ruleDecisionSnapshotFor(game, observerId),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (!failurePolicy.isIsolatable(failure)) throw failure
                usedConservativeSnapshots = true
                snapshotFailures += GameSnapshotRestoreFailure(
                    gameId = game.id,
                    ruleModuleId = moduleRegistry.getModule(game.tableState.config).id,
                    cause = failure,
                )
                humanIds.associateWith { observerId ->
                    RestoredObserverSnapshot(table = game.conservativeSnapshotFor(observerId), decision = null)
                }
            }
            snapshots.forEach { (observerId, restored) ->
                gameSnapshots.setSnapshot(observerId, restored.table)
                gameSnapshots.setRuleDecisionSnapshot(
                    gameId = game.id,
                    observerId = observerId,
                    snapshot = restored.decision,
                )
            }
            if (usedConservativeSnapshots) {
                // 保守快照的對局仍要重新排定計時器與 AI，否則對局會停住；這局的規則失敗已經記錄。
                try {
                    decisionAvailabilityService.reconcile(game.id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    if (!failurePolicy.isIsolatable(failure)) throw failure
                }
            } else {
                decisionAvailabilityService.reconcile(game.id)
            }
        }
        return ServerSessionStateRestoreResult(conflicts, snapshotFailures)
    }

    /**
     * 同一局完成計算、尚未寫入觀察者倉庫的快照。
     *
     * @property table 已裁切的桌況。
     * @property decision 僅屬於該觀察者的選擇資料。
     */
    private data class RestoredObserverSnapshot(
        val table: TableStateSnapshot,
        val decision: RuleDecisionSnapshot?,
    )

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
