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
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.PlayerIdentitySender
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 將房間事件與收件玩家可見的最新快照組成封包；沒有快照或玩家不在線時不送出。
 *
 * @property roomSnapshotRepository 玩家可見的房間快照來源。
 * @property serverHolder 目前伺服器與有效收件玩家。
 * @property playerIdentities 房間真人參與者的普通名稱同步器。
 * @property json 封包序列化設定。
 * @property networkRegistries 規則擴充資料的線路轉換註冊表。
 */
@Single(binds = [RoomEventPublisher::class])
class RoomEventPublisherImpl(
    private val roomSnapshotRepository: RoomSnapshotRepository,
    private val serverHolder: FabricServerHolder,
    private val playerIdentities: PlayerIdentitySender,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
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
        val snapshot = roomSnapshotRepository.getSnapshot(roomId, targetPlayerId) ?: return
        val player = serverHolder.findPlayer(targetPlayerId) ?: return
        val payload = RoomUpdatePayloadDto(
            roomId = roomId.toString(),
            event = event,
            snapshot = snapshot.toDto(networkRegistries),
        )
        playerIdentities.send(targetPlayerId, snapshot.playerIds.filterNot { it in snapshot.aiPlayerIds })
        MahjongChannels.roomUpdate.sendTo(player, json, payload)
    }
}
