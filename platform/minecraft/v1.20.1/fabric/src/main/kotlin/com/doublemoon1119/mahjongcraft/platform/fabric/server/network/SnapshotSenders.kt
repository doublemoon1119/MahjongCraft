package com.doublemoon1119.mahjongcraft.platform.fabric.server.network

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.observer.model.ObserverSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverSnapshotSender
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
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

/** 把 `SyncRoomSnapshotUseCase` 已寫入 repository 的快照主動送給指定玩家。 */
@Single
class RoomSnapshotSender(
    private val snapshots: RoomSnapshotRepository,
    private val serverHolder: FabricServerHolder,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) {
    /** 若玩家在線且快照存在，送出一份不伴隨房間事件的同步 payload。 */
    suspend fun send(roomId: Uuid, playerId: Uuid) {
        val player = serverHolder.findPlayer(playerId) ?: return
        val snapshot = snapshots.getSnapshot(roomId, playerId) ?: return
        MahjongChannels.roomSnapshot.sendTo(
            player,
            json,
            RoomSnapshotSyncPayloadDto(roomId.toString(), snapshot.toDto(networkRegistries)),
        )
    }
}

/** 把 `SyncGameSnapshotUseCase` 已寫入 repository 的快照主動送給指定玩家。 */
@Single
class GameSnapshotSender(
    private val snapshots: GameSnapshotRepository,
    private val serverHolder: FabricServerHolder,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) {
    /** 若玩家在線且快照存在，送出一份不伴隨遊戲動作的同步 payload。 */
    suspend fun send(gameId: Uuid, playerId: Uuid) {
        val player = serverHolder.findPlayer(playerId) ?: return
        val snapshot = snapshots.getSnapshot(gameId, playerId) ?: return
        val preparation = snapshots.getRoundPreparationSnapshot(gameId, playerId)
        MahjongChannels.gameSnapshot.sendTo(
            player,
            json,
            GameSnapshotSyncPayloadDto(gameId.toString(), snapshot.toDto(networkRegistries), preparation?.toDto()),
        )
    }
}

/**
 * [ObserverSnapshotSender] 的 Fabric 實作：房間內容走 [MahjongChannels.roomSnapshot]、對局內容走
 * [MahjongChannels.gameSnapshot]，兩者都不存在時走 [MahjongChannels.snapshotCleared] 清除玩家手上的
 * 內容。
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
