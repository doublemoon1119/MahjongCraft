package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdateEventDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.PlayerIdentitySender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableStateDelivery
import kotlinx.serialization.json.Json
import net.minecraft.server.network.ServerPlayerEntity
import kotlin.uuid.Uuid

/**
 * 將房間事件與收件玩家可見的最新快照組成封包；沒有快照或玩家不在線時不送出。
 *
 * @property roomSnapshotRepository 玩家可見的房間快照來源。
 * @property tableStateDelivery 統一檢查桌子來源並序列化桌況封包交付的邊界。
 * @property authoritativeStateStore 讀取仍存在的權威房間，避免使用已移除的 read-side 快照。
 * @property serverHolder 目前伺服器與有效收件玩家。
 * @property playerIdentities 房間真人參與者的普通名稱同步器。
 * @property json 封包序列化設定。
 * @property networkRegistries 規則擴充資料的線路轉換註冊表。
 */
class RoomEventPublisherImpl(
    private val roomSnapshotRepository: RoomSnapshotRepository,
    private val tableStateDelivery: FabricTableStateDelivery,
    private val authoritativeStateStore: AuthoritativeStateStore,
    private val serverHolder: FabricServerHolder,
    private val playerIdentities: PlayerIdentitySender,
    private val json: Json,
    private val networkRegistries: NetworkDtoRegistries,
) : RoomEventPublisher {
    override suspend fun publishJoin(roomId: Uuid, targetPlayerId: Uuid, joinedPlayerId: Uuid, reason: JoinReason) {
        send(roomId, targetPlayerId, RoomUpdateEventDto.Join(joinedPlayerId.toString(), reason.toDto()))
    }

    override suspend fun publishLeave(roomId: Uuid, targetPlayerId: Uuid, leftPlayerId: Uuid, reason: LeaveReason) {
        send(roomId, targetPlayerId, RoomUpdateEventDto.Leave(leftPlayerId.toString(), reason.toDto()))
    }

    override suspend fun publishReady(roomId: Uuid, targetPlayerId: Uuid, readyPlayerId: Uuid, isReady: Boolean) {
        send(roomId, targetPlayerId, RoomUpdateEventDto.Ready(readyPlayerId.toString(), isReady))
    }

    override suspend fun publishConfigChanged(roomId: Uuid, targetPlayerId: Uuid, newConfig: MahjongRuleConfig) {
        send(roomId, targetPlayerId, RoomUpdateEventDto.ConfigChanged(newConfig.toDto(networkRegistries)))
    }

    /**
     * 傳送房間事件、目前快照與真人玩家名稱。
     *
     * @param roomId 要查詢快照的房間。
     * @param targetPlayerId 快照的觀察者及封包收件玩家。
     * @param event 要與快照一起傳送的房間事件。
     */
    private suspend fun send(roomId: Uuid, targetPlayerId: Uuid, event: RoomUpdateEventDto) {
        val ticket = tableStateDelivery.capture(roomId) ?: return
        tableStateDelivery.prepareAndSend(
            ticket = ticket,
            prepare = {
                authoritativeStateStore.getRoom(roomId) ?: return@prepareAndSend null
                val snapshot = roomSnapshotRepository.getSnapshot(roomId, targetPlayerId) ?: return@prepareAndSend null
                val player = serverHolder.findPlayer(targetPlayerId) ?: return@prepareAndSend null
                playerIdentities.send(targetPlayerId, snapshot.playerIds.filterNot { it in snapshot.aiPlayerIds })
                PreparedRoomUpdate(
                    player = player,
                    payload = RoomUpdatePayloadDto(
                        roomId = roomId.toString(),
                        event = event,
                        snapshot = snapshot.toDto(networkRegistries),
                    ),
                )
            },
            send = { prepared -> MahjongChannels.roomUpdate.sendTo(prepared.player, json, prepared.payload) },
        )
    }

    /**
     * 已在主執行緒準備完成、可在最後檢查後立即送出的房間更新。
     *
     * @property player 已驗證仍在線的收件玩家。
     * @property payload 已序列化所需內容的房間更新 payload。
     */
    private data class PreparedRoomUpdate(
        val player: ServerPlayerEntity,
        val payload: RoomUpdatePayloadDto,
    )
}
