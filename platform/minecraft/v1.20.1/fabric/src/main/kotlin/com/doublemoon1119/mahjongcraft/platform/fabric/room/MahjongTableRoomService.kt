package com.doublemoon1119.mahjongcraft.platform.fabric.room

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SyncGameSnapshotUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.CreateRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.JoinRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.SyncRoomSnapshotUseCase
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.network.GameSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.network.RoomSnapshotSender
import kotlinx.coroutines.launch
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import org.koin.core.annotation.Single
import kotlin.uuid.toKotlinUuid

/** 把 Minecraft 麻將桌右鍵互動路由到既有 Room／Game use case 的正式平台進場服務。 */
@Single
class MahjongTableRoomService(
    private val scope: AppCoroutineScope,
    private val roomRepository: RoomRepository,
    private val gameRepository: GameRepository,
    private val createRoom: CreateRoomUseCase,
    private val joinRoom: JoinRoomUseCase,
    private val syncRoom: SyncRoomSnapshotUseCase,
    private val syncGame: SyncGameSnapshotUseCase,
    private val roomSnapshotSender: RoomSnapshotSender,
    private val gameSnapshotSender: GameSnapshotSender,
) {
    /** 依麻將桌目前處於空桌、等待室或遊戲階段，建立、加入或重新同步玩家狀態。 */
    fun interact(table: MahjongTableBlockEntity, player: ServerPlayerEntity) {
        val tableId = table.tableId
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val game = gameRepository.getTableState(tableId)
            if (game != null) {
                if (game.players.none { it.id == playerId }) {
                    sendMessage(player, "mahjongcraft.message.game_already_started")
                    return@launch
                }
                syncGame(tableId, playerId)
                gameSnapshotSender.send(tableId, playerId)
                return@launch
            }

            val room = roomRepository.getRoom(tableId)
            if (room == null) {
                when (val result = createRoom(tableId, playerId, RiichiRuleConfig())) {
                    is Outcome.Success -> {
                        syncRoom(tableId, playerId)
                        roomSnapshotSender.send(tableId, playerId)
                        sendMessage(player, "mahjongcraft.message.game_created")
                    }
                    is Outcome.Error -> sendRoomError(player, result.error)
                }
                return@launch
            }

            if (playerId in room.playerIds) {
                syncRoom(tableId, playerId)
                roomSnapshotSender.send(tableId, playerId)
                return@launch
            }

            // 先建立 observer snapshot，讓 JoinRoomUseCase 更新 observer 後，RoomEventPublisher 能把
            // 加入事件與正確的 isInRoom=true 快照一併送給新玩家。
            syncRoom(tableId, playerId)
            when (val result = joinRoom(tableId, playerId)) {
                is Outcome.Success -> sendMessage(player, "mahjongcraft.message.game_joined")
                is Outcome.Error -> sendRoomError(player, result.error)
            }
        }
    }

    /** 把訊息切回 Minecraft server thread 後送給玩家。 */
    private fun sendMessage(player: ServerPlayerEntity, translationKey: String) {
        player.server.execute { player.sendMessage(Text.translatable(translationKey)) }
    }

    /** 將可辨識的房間錯誤映射成玩家訊息，其餘錯誤暫時使用通用加入失敗提示。 */
    private fun sendRoomError(player: ServerPlayerEntity, error: RoomError) {
        val translationKey = when (error) {
            is RoomError.PlayerAlreadyInAnotherGame -> "mahjongcraft.message.player_already_in_game"
            else -> "mahjongcraft.message.game_join_failed"
        }
        sendMessage(player, translationKey)
    }
}
