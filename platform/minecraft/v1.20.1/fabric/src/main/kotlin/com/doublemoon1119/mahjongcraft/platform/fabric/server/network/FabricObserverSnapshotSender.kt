package com.doublemoon1119.mahjongcraft.platform.fabric.server.network

import com.doublemoon1119.mahjongcraft.flow.common.observer.model.ObserverSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverSnapshotSender
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.SnapshotClearedPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * [ObserverSnapshotSender] 的 Fabric 實作，依內容選擇頻道：
 *
 * - [ObserverSnapshot.OfRoom]：[MahjongChannels.roomSnapshot]。
 * - [ObserverSnapshot.OfGame]：[MahjongChannels.gameSnapshot]。
 * - `null`：[MahjongChannels.snapshotCleared]，清除玩家手上的內容。
 *
 * 這些 payload 只更新客戶端保存的狀態，不攜帶動作語意也不產生文字訊息。玩家不在線時直接跳過，下次
 * 再成為觀察者時會重新收到完整內容。
 */
@Single(binds = [ObserverSnapshotSender::class])
class FabricObserverSnapshotSender(
    private val serverHolder: FabricServerHolder,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) : ObserverSnapshotSender {
    override suspend fun send(id: Uuid, observerId: Uuid, snapshot: ObserverSnapshot?) {
        val player = serverHolder.findPlayer(observerId) ?: return
        when (snapshot) {
            is ObserverSnapshot.OfRoom -> MahjongChannels.roomSnapshot.sendTo(
                player,
                json,
                RoomSnapshotSyncPayloadDto(id.toString(), snapshot.room.toDto(networkRegistries)),
            )

            is ObserverSnapshot.OfGame -> MahjongChannels.gameSnapshot.sendTo(
                player,
                json,
                GameSnapshotSyncPayloadDto(
                    gameId = id.toString(),
                    snapshot = snapshot.game.toDto(networkRegistries),
                    roundPreparation = snapshot.roundPreparation?.toDto(),
                ),
            )

            null -> MahjongChannels.snapshotCleared.sendTo(player, json, SnapshotClearedPayloadDto(id = id.toString()))
        }
    }
}
