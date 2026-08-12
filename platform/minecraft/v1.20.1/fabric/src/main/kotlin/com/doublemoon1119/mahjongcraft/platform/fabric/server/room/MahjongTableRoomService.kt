package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SyncGameSnapshotUseCase
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.CreateRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.JoinRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.LeaveRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.SyncRoomSnapshotUseCase
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.GameSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.RoomSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import kotlinx.coroutines.launch
import net.minecraft.server.network.ServerPlayerEntity
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/** 把 Minecraft 麻將桌右鍵互動路由到既有 Room／Game use case 的正式平台進場服務。 */
@Single
class MahjongTableRoomService(
    private val scope: AppCoroutineScope,
    private val roomRepository: RoomRepository,
    private val gameRepository: GameRepository,
    private val membershipRepository: PlayerMembershipRepository,
    private val createRoom: CreateRoomUseCase,
    private val joinRoom: JoinRoomUseCase,
    private val leaveRoom: LeaveRoomUseCase,
    private val syncRoom: SyncRoomSnapshotUseCase,
    private val syncGame: SyncGameSnapshotUseCase,
    private val roomSnapshotSender: RoomSnapshotSender,
    private val gameSnapshotSender: GameSnapshotSender,
    private val feedbackPublisher: MinecraftPlayerFeedbackPublisher,
) {
    /** 依麻將桌目前處於空桌、等待室或遊戲階段，建立、加入或重新同步玩家狀態。 */
    fun interact(table: MahjongTableBlockEntity, player: ServerPlayerEntity) {
        val tableId = table.tableId
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val game = gameRepository.getTableState(tableId)
            if (game != null) {
                if (game.players.none { it.id == playerId }) {
                    feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameAlreadyStarted)
                    return@launch
                }
                if (!membershipRepository.claim(playerId, tableId)) {
                    feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerAlreadyInGame)
                    return@launch
                }
                syncGame(tableId, playerId)
                gameSnapshotSender.send(tableId, playerId)
                return@launch
            }

            val room = roomRepository.getRoom(tableId)
            if (room == null) {
                when (val result = createRoom(tableId, playerId, GameConfig(RiichiRuleConfig()))) {
                    is Outcome.Success -> {
                        syncRoom(tableId, playerId)
                        roomSnapshotSender.send(tableId, playerId)
                        feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameCreated)
                    }
                    is Outcome.Error -> publishRoomError(playerId, result.error)
                }
                return@launch
            }

            if (playerId in room.playerIds) {
                if (!membershipRepository.claim(playerId, tableId)) {
                    feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerAlreadyInGame)
                    return@launch
                }
                syncRoom(tableId, playerId)
                roomSnapshotSender.send(tableId, playerId)
                return@launch
            }

            // 先建立 observer snapshot，讓 JoinRoomUseCase 更新 observer 後，RoomEventPublisher 能把
            // 加入事件與正確的 isInRoom=true 快照一併送給新玩家。
            syncRoom(tableId, playerId)
            when (val result = joinRoom(tableId, playerId)) {
                is Outcome.Success -> feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameJoined)
                is Outcome.Error -> publishRoomError(playerId, result.error)
            }
        }
    }

    /** 讓玩家以蹲下右鍵離開目前麻將桌的等待階段；進行中的對局暫時禁止離開。 */
    fun leave(table: MahjongTableBlockEntity, player: ServerPlayerEntity) {
        val tableId = table.tableId
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            if (membershipRepository.getTableId(playerId) != tableId) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            if (gameRepository.getTableState(tableId) != null) {
                feedbackPublisher.publish(
                    playerId,
                    MinecraftPlayerFeedback.GameLeaveDeniedWhilePlaying,
                )
                return@launch
            }

            val room = roomRepository.getRoom(tableId)
            if (room == null || playerId !in room.playerIds) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            val wasHost = room.hostId == playerId
            when (leaveRoom(tableId, playerId)) {
                is Outcome.Success -> feedbackPublisher.publish(
                    playerId,
                    MinecraftRoomFeedbackResolver.successfulLeave(wasHost),
                )
                is Outcome.Error -> feedbackPublisher.publish(
                    playerId,
                    MinecraftPlayerFeedback.GameLeaveFailed,
                )
            }
        }
    }

    /** 將可辨識的房間錯誤映射成結構化回饋，其餘錯誤使用通用加入失敗提示。 */
    private fun publishRoomError(playerId: Uuid, error: RoomError) {
        feedbackPublisher.publish(playerId, MinecraftRoomFeedbackResolver.joinError(error))
    }
}
