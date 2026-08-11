package com.doublemoon1119.mahjongcraft.platform.fabric.server.player

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.LeaveRoomUseCase
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.DisconnectedPlayerPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 依伺服器政策處理玩家離線、逾時離開與重連取消。 */
@Single
class DisconnectedPlayerLifecycleService(
    private val scope: AppCoroutineScope,
    private val config: MinecraftServerConfig,
    private val membershipRepository: PlayerMembershipRepository,
    private val roomRepository: RoomRepository,
    private val gameRepository: GameRepository,
    private val leaveRoom: LeaveRoomUseCase,
) {
    private val pendingLeaveJobs = mutableMapOf<Uuid, Job>()
    private val pendingLeaveJobsLock = Any()

    /** 玩家重連時取消尚未到期的離線離開工作。 */
    fun onConnected(playerId: Uuid) {
        synchronized(pendingLeaveJobsLock) { pendingLeaveJobs.remove(playerId) }?.cancel()
    }

    /** 玩家斷線時依政策保留座位、立即離開或安排逾時離開。 */
    fun onDisconnected(playerId: Uuid) {
        onConnected(playerId)
        when (config.disconnectedPlayerPolicy) {
            DisconnectedPlayerPolicy.KEEP_SEAT -> Unit
            DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY -> scope.launch { leaveWaitingRoom(playerId) }
            DisconnectedPlayerPolicy.LEAVE_AFTER_TIMEOUT -> scheduleTimeout(playerId)
        }
    }

    /** 依設定秒數安排離開工作，完成或取消後移除工作索引。 */
    private fun scheduleTimeout(playerId: Uuid) {
        val job = scope.launch {
            delay(config.disconnectedPlayerTimeoutSeconds * 1_000)
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
        val tableId = membershipRepository.getTableId(playerId) ?: return
        if (gameRepository.getTableState(tableId) != null) return
        val room = roomRepository.getRoom(tableId) ?: return
        if (playerId !in room.playerIds) return
        leaveRoom(tableId, playerId)
    }
}
