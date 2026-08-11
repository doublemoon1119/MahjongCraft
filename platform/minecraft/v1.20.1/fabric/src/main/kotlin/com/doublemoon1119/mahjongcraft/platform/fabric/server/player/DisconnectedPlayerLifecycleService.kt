package com.doublemoon1119.mahjongcraft.platform.fabric.server.player

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.LeaveRoomUseCase
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.DisconnectedPlayerPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** 依伺服器政策處理玩家離線、逾時離開與重連取消。 */
@Single
class DisconnectedPlayerLifecycleService(
    private val scope: AppCoroutineScope,
    private val configState: MinecraftServerConfigState,
    private val membershipRepository: PlayerMembershipRepository,
    private val roomRepository: RoomRepository,
    private val gameRepository: GameRepository,
    private val leaveRoom: LeaveRoomUseCase,
) {
    /** 記錄斷線政策、延遲工作與略過離開的原因。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 依玩家 UUID 保存尚未到期的延遲離開工作。 */
    private val pendingLeaveJobs = mutableMapOf<Uuid, Job>()

    /** 保護 [pendingLeaveJobs] 的跨 coroutine 存取。 */
    private val pendingLeaveJobsLock = Any()

    /** 玩家重連時取消尚未到期的離線離開工作。 */
    fun onConnected(playerId: Uuid) {
        val pendingJob = synchronized(pendingLeaveJobsLock) { pendingLeaveJobs.remove(playerId) }
        if (pendingJob != null) {
            pendingJob.cancel()
            logger.debug("Cancelled pending disconnected-player timeout for player {} after reconnect", playerId)
        }
    }

    /** 玩家斷線時依政策保留座位、立即離開或安排逾時離開。 */
    fun onDisconnected(playerId: Uuid) {
        onConnected(playerId)
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
