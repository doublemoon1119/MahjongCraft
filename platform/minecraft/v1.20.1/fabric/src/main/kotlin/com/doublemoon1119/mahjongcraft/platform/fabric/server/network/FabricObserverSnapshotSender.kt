package com.doublemoon1119.mahjongcraft.platform.fabric.server.network

import com.doublemoon1119.mahjongcraft.flow.common.observer.model.ObserverSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverSnapshotSender
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.SnapshotClearedPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.ReadinessAnalysisDtoMapper
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableStateDelivery
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableRemovedPayloadDto
import net.minecraft.server.network.ServerPlayerEntity
import kotlin.uuid.Uuid

/**
 * [ObserverSnapshotSender] 的 Fabric 實作，依內容選擇頻道：
 *
 * - [ObserverSnapshot.OfRoom]：[MahjongChannels.roomSnapshot]。
 * - [ObserverSnapshot.OfGame]：[MahjongChannels.gameSnapshot]。
 * - `null`：[MahjongChannels.snapshotCleared]，清除玩家手上的內容。
 *
 * 所有可重建桌況的 payload 都經過桌級交付邊界；若桌子來源已失效，不會再重建舊狀態。
 *
 * @property serverHolder 目前伺服器與有效收件玩家。
 * @property tableStateDelivery 統一檢查桌子來源並序列化桌況封包交付的邊界。
 * @property authoritativeStateStore 確認非空快照仍有對應的權威 Room 或 Game。
 * @property automaticControlSnapshotSender 自動操作偏好的權威同步器。
 * @property analysisDtoMapper 規則中立的手牌分析線路轉換器。
 * @property playerIdentities 已授權快照涉及的真人名稱同步器。
 * @property json 封包序列化設定。
 * @property networkRegistries 規則擴充資料的線路轉換註冊表。
 */
class FabricObserverSnapshotSender(
    private val serverHolder: FabricServerHolder,
    private val tableStateDelivery: FabricTableStateDelivery,
    private val authoritativeStateStore: AuthoritativeStateStore,
    private val automaticControlSnapshotSender: AutomaticControlSnapshotSender,
    private val analysisDtoMapper: ReadinessAnalysisDtoMapper,
    private val playerIdentities: PlayerIdentitySender,
    private val json: NetworkJson,
    private val networkRegistries: NetworkDtoRegistries,
) : ObserverSnapshotSender {
    override suspend fun send(id: Uuid, observerId: Uuid, snapshot: ObserverSnapshot?) {
        val ticket = tableStateDelivery.capture(id) ?: return
        val delivered = tableStateDelivery.prepareAndSend(
            ticket = ticket,
            prepare = {
                val player = serverHolder.findPlayer(observerId) ?: return@prepareAndSend null
                when (snapshot) {
                    is ObserverSnapshot.OfRoom -> {
                        if (authoritativeStateStore.getRoom(id) == null) return@prepareAndSend null
                        playerIdentities.send(observerId, snapshot.room.playerIds.filterNot { it in snapshot.room.aiPlayerIds })
                        PreparedSnapshot.Room(
                            player = player,
                            payload = RoomSnapshotSyncPayloadDto(id.toString(), snapshot.room.toDto(networkRegistries)),
                        )
                    }

                    is ObserverSnapshot.OfGame -> {
                        if (authoritativeStateStore.getGame(id) == null) return@prepareAndSend null
                        playerIdentities.send(observerId, snapshot.game.players.map { it.id }.filterNot { it in snapshot.aiPlayerIds })
                        PreparedSnapshot.Game(
                            player = player,
                            payload = GameSnapshotSyncPayloadDto(
                                gameId = id.toString(),
                                snapshot = snapshot.game.toDto(networkRegistries),
                                aiPlayerIds = snapshot.aiPlayerIds.map { it.toString() },
                                roundPreparation = snapshot.roundPreparation?.toDto(),
                                handReadinessAnalysis = snapshot.handReadinessAnalysis?.let { readiness ->
                                    analysisDtoMapper.toDto(readiness.ruleModuleId, readiness.analysis)
                                },
                            ),
                        )
                    }

                    null -> {
                        val state = authoritativeStateStore.state.value
                        if (id in state.rooms || id in state.games) return@prepareAndSend null
                        PreparedSnapshot.Cleared(
                            player = player,
                            payload = SnapshotClearedPayloadDto(id = id.toString()),
                        )
                    }
                }
            },
            send = { prepared ->
                when (prepared) {
                    is PreparedSnapshot.Room -> MahjongChannels.roomSnapshot.sendTo(prepared.player, json, prepared.payload)
                    is PreparedSnapshot.Game -> MahjongChannels.gameSnapshot.sendTo(prepared.player, json, prepared.payload)
                    is PreparedSnapshot.Cleared -> {
                        val state = authoritativeStateStore.state.value
                        if (id !in state.rooms && id !in state.games) {
                            MahjongChannels.snapshotCleared.sendTo(prepared.player, json, prepared.payload)
                        }
                    }
                }
            },
        )
        if (delivered && snapshot is ObserverSnapshot.OfGame) {
            automaticControlSnapshotSender.send(id, observerId)
        }
    }

    override suspend fun invalidateVenue(
        id: Uuid,
        observerId: Uuid,
        requestId: Uuid,
    ) {
        val ticket = tableStateDelivery.removalTicket(id, requestId) ?: return
        tableStateDelivery.sendRemoval(ticket) {
            val player = serverHolder.findPlayer(observerId) ?: return@sendRemoval
            MahjongChannels.tableRemoved.sendTo(player, json, TableRemovedPayloadDto(id.toString()))
        }
    }

    /** 已在主執行緒準備完成、可在最後檢查後立即送出的快照 payload。 */
    private sealed interface PreparedSnapshot {
        /** 已驗證仍在線的收件玩家。 */
        val player: ServerPlayerEntity

        /**
         * 房間快照與其收件玩家。
         *
         * @property player 已驗證仍在線的收件玩家。
         * @property payload 已序列化的房間快照 payload。
         */
        data class Room(
            override val player: ServerPlayerEntity,
            val payload: RoomSnapshotSyncPayloadDto,
        ) : PreparedSnapshot

        /**
         * 對局快照與其收件玩家。
         *
         * @property player 已驗證仍在線的收件玩家。
         * @property payload 已序列化的對局快照 payload。
         */
        data class Game(
            override val player: ServerPlayerEntity,
            val payload: GameSnapshotSyncPayloadDto,
        ) : PreparedSnapshot

        /**
         * 清除快照與其收件玩家。
         *
         * @property player 已驗證仍在線的收件玩家。
         * @property payload 已序列化的快照清除 payload。
         */
        data class Cleared(
            override val player: ServerPlayerEntity,
            val payload: SnapshotClearedPayloadDto,
        ) : PreparedSnapshot
    }
}
