package com.doublemoon1119.mahjongcraft.platform.fabric.server.player

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SyncGameSnapshotUseCase
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.LeaveRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.SyncRoomSnapshotUseCase
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.GameSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.RoomSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.DisconnectedPlayerPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * 依伺服器政策處理玩家連線生命週期：斷線時的離開政策、重連時取消逾時離開，以及重連時的快照補送。
 *
 * 原名 `DisconnectedPlayerLifecycleService`——加上重連補送快照的職責後，這個服務已經不只處理
 * 「斷線玩家」，改成涵蓋 [onConnected]／[onDisconnected] 兩端的完整連線生命週期，因此改用現在這個
 * 名稱。
 */
@Single
class PlayerConnectionLifecycleService(
    private val scope: AppCoroutineScope,
    private val configState: MinecraftServerConfigState,
    private val membershipRepository: PlayerMembershipRepository,
    private val roomRepository: RoomRepository,
    private val gameRepository: GameRepository,
    private val leaveRoom: LeaveRoomUseCase,
    private val syncRoom: SyncRoomSnapshotUseCase,
    private val syncGame: SyncGameSnapshotUseCase,
    private val roomSnapshotSender: RoomSnapshotSender,
    private val gameSnapshotSender: GameSnapshotSender,
) {
    /** 記錄斷線政策、延遲工作與略過離開的原因。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 依玩家 UUID 保存尚未到期的延遲離開工作。 */
    private val pendingLeaveJobs = mutableMapOf<Uuid, Job>()

    /** 保護 [pendingLeaveJobs] 的跨 coroutine 存取。 */
    private val pendingLeaveJobsLock = Any()

    /**
     * 玩家重連時取消尚未到期的離線離開工作，並主動重新推送一份快照。
     *
     * 客戶端重新登入後沒有任何既有快照——牌局管理的麻將牌 entity 在收到快照前恆定顯示
     * [UNKNOWN_TILE_ASSET_KEY] 占位貼圖（見 `MahjongTileEntity` KDoc）。過去只有玩家重新右鍵桌子
     * （[MahjongTableRoomService.interact]）才會觸發 `syncGame`／`syncRoom` 補送快照，玩家單純
     * 重新登入、還沒來得及再次互動桌子的這段期間，手牌會一直卡在 unknown，這是遊戲內實際回報過的
     * 問題。比照 `interact()` 的同一套「先同步 read-side 快照、再主動推送」模式，讓重連當下就補上。
     *
     * 只由真正的重連（`ServerPlayConnectionEvents.JOIN`）呼叫——[onDisconnected] 過去會呼叫這個方法
     * 來重用「取消 pending leave job」邏輯，但這代表每次斷線也會誤觸發一次補送快照（送給正在斷線、
     * 已經被標記 removed 的舊 entity，完全無效），這是實際除錯時發現的問題，因此把「取消 pending
     * leave job」抽成 [cancelPendingLeaveJob]，[onDisconnected] 改呼叫那個，不再呼叫這個方法。
     */
    fun onConnected(playerId: Uuid) {
        cancelPendingLeaveJob(playerId)
        scope.launch { resyncSnapshotAfterReconnect(playerId) }
    }

    /** 取消玩家尚未到期的逾時離開工作；重連與斷線都需要這個動作，見 [onConnected]／[onDisconnected]。 */
    private fun cancelPendingLeaveJob(playerId: Uuid) {
        val pendingJob = synchronized(pendingLeaveJobsLock) { pendingLeaveJobs.remove(playerId) }
        if (pendingJob != null) {
            pendingJob.cancel()
            logger.debug("Cancelled pending disconnected-player timeout for player {}", playerId)
        }
    }

    /** 依玩家目前的房間歸屬，補送一份對局或房間快照——沒有歸屬時代表玩家不在任何桌子上，略過。 */
    private suspend fun resyncSnapshotAfterReconnect(playerId: Uuid) {
        val tableId = membershipRepository.getTableId(playerId)
        if (tableId == null) {
            logger.debug("Skipped reconnect snapshot resync for player {} because no membership exists", playerId)
            return
        }
        if (gameRepository.getTableState(tableId) != null) {
            syncGame(tableId, playerId)
            gameSnapshotSender.send(tableId, playerId)
            logger.debug("Resynced game snapshot for player {} in table {} after reconnect", playerId, tableId)
            return
        }
        if (roomRepository.getRoom(tableId) != null) {
            syncRoom(tableId, playerId)
            roomSnapshotSender.send(tableId, playerId)
            logger.debug("Resynced room snapshot for player {} in table {} after reconnect", playerId, tableId)
        }
    }

    /** 玩家斷線時依政策保留座位、立即離開或安排逾時離開。 */
    fun onDisconnected(playerId: Uuid) {
        cancelPendingLeaveJob(playerId)
        val config = configState.current
        logger.debug("Applying disconnected-player policy {} to player {}", config.disconnectedPlayerPolicy, playerId)
        when (config.disconnectedPlayerPolicy) {
            DisconnectedPlayerPolicy.KEEP_SEAT -> Unit
            DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY -> scope.launch { leaveWaitingRoom(playerId) }
            DisconnectedPlayerPolicy.LEAVE_AFTER_TIMEOUT -> scheduleTimeout(
                playerId,
                config.disconnectedPlayerTimeoutSeconds,
            )
        }
    }

    /** 依設定秒數安排離開工作，完成或取消後移除工作索引。 */
    private fun scheduleTimeout(playerId: Uuid, timeoutSeconds: Long) {
        logger.debug("Scheduled disconnected-player timeout of {} second(s) for player {}", timeoutSeconds, playerId)
        val job = scope.launch {
            delay(timeoutSeconds.seconds)
            logger.debug("Disconnected-player timeout expired for player {}", playerId)
            leaveWaitingRoom(playerId)
        }
        synchronized(pendingLeaveJobsLock) { pendingLeaveJobs[playerId] = job }
        job.invokeOnCompletion {
            synchronized(pendingLeaveJobsLock) {
                if (pendingLeaveJobs[playerId] === job) pendingLeaveJobs.remove(playerId)
            }
        }
    }

    /** 僅讓仍處於等待室的玩家離開；進行中的對局一律保留座位。 */
    private suspend fun leaveWaitingRoom(playerId: Uuid) {
        val tableId = membershipRepository.getTableId(playerId)
        if (tableId == null) {
            logger.debug("Skipped disconnected-player leave for player {} because no membership exists", playerId)
            return
        }
        if (gameRepository.getTableState(tableId) != null) {
            logger.debug("Retained disconnected player {} because game {} has already started", playerId, tableId)
            return
        }
        val room = roomRepository.getRoom(tableId)
        if (room == null || playerId !in room.playerIds) {
            logger.debug("Skipped disconnected-player leave for player {} because room {} is unavailable", playerId, tableId)
            return
        }
        when (val result = leaveRoom(tableId, playerId)) {
            is Outcome.Success -> logger.debug(
                "Removed disconnected player {} from waiting room {}",
                playerId,
                tableId,
            )
            is Outcome.Error -> logger.debug(
                "Failed to remove disconnected player {} from waiting room {}: {}",
                playerId,
                tableId,
                result.error,
            )
        }
    }
}
