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
 * [RoomEventPublisher] 的 Fabric 實作，四個方法都收斂到同一個 [send]——組出對應的 [RoomUpdateEventDto]
 * 變體，讀出目標玩家目前的房間快照（呼叫端已經先寫進 [roomSnapshotRepository]），一起包成
 * [RoomUpdatePayloadDto] 送出。目標玩家不在線就直接跳過，理由同 [GameEventPublisherImpl]。
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
