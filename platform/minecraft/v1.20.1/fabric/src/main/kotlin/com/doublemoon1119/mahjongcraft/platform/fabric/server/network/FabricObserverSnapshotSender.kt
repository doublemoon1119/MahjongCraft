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
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.ReadinessAnalysisDtoMapper
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
 *
 * @property serverHolder 目前伺服器與有效收件玩家。
 * @property automaticControlSnapshotSender 自動操作偏好的權威同步器。
 * @property analysisDtoMapper 規則中立的手牌分析線路轉換器。
 * @property playerIdentities 已授權快照涉及的真人名稱同步器。
 * @property json 封包序列化設定。
 * @property networkRegistries 規則擴充資料的線路轉換註冊表。
 */
@Single(binds = [ObserverSnapshotSender::class])
class FabricObserverSnapshotSender(
    private val serverHolder: FabricServerHolder,
    private val automaticControlSnapshotSender: AutomaticControlSnapshotSender,
    private val analysisDtoMapper: ReadinessAnalysisDtoMapper,
    private val playerIdentities: PlayerIdentitySender,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) : ObserverSnapshotSender {
    override suspend fun send(id: Uuid, observerId: Uuid, snapshot: ObserverSnapshot?) {
        val player = serverHolder.findPlayer(observerId) ?: return
        when (snapshot) {
            is ObserverSnapshot.OfRoom -> {
                playerIdentities.send(observerId, snapshot.room.playerIds.filterNot { it in snapshot.room.aiPlayerIds })
                MahjongChannels.roomSnapshot.sendTo(
                    player,
                    json,
                    RoomSnapshotSyncPayloadDto(id.toString(), snapshot.room.toDto(networkRegistries)),
                )
                automaticControlSnapshotSender.clear(observerId)
            }

            is ObserverSnapshot.OfGame -> {
                playerIdentities.send(observerId, snapshot.game.players.map { it.id }.filterNot { it in snapshot.aiPlayerIds })
                MahjongChannels.gameSnapshot.sendTo(
                    player,
                    json,
                    GameSnapshotSyncPayloadDto(
                        gameId = id.toString(),
                        snapshot = snapshot.game.toDto(networkRegistries),
                        aiPlayerIds = snapshot.aiPlayerIds.map { it.toString() },
                        roundPreparation = snapshot.roundPreparation?.toDto(),
                        handReadinessAnalysis = snapshot.handReadinessAnalysis?.let { readiness ->
                            analysisDtoMapper.toDto(readiness.ruleModuleId, readiness.analysis)
                        },
                    ),
                )
                automaticControlSnapshotSender.send(id, observerId)
            }

            null -> {
                MahjongChannels.snapshotCleared.sendTo(player, json, SnapshotClearedPayloadDto(id = id.toString()))
                automaticControlSnapshotSender.clear(observerId)
            }
        }
    }
}
