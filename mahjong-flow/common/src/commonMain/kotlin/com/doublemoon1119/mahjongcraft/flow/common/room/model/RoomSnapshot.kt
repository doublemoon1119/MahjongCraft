package com.doublemoon1119.mahjongcraft.flow.common.room.model

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlin.uuid.Uuid

/**
 * 針對特定觀察者視角產生的房間唯讀快照。
 *
 * 相較於 [Room]，額外附加了與觀察者身分相關的衍生欄位（[isHost]、[isInRoom]），
 * 供客戶端或外部通知服務直接使用，避免每個消費端重複計算相同的判斷邏輯。
 *
 * @property id 房間的唯一識別碼。
 * @property hostId 房主的玩家 Uuid。
 * @property config 該房間採用的麻將規則配置。
 * @property playerIds 目前房間內所有玩家（含房主與 AI）的 Uuid 集合。
 * @property readyPlayerIds 已標記為「準備完成」的玩家 Uuid 集合。
 * @property aiPlayerIds 由房主新增的 AI 玩家 Uuid 集合。
 * @property canStart 房間是否符合開局條件。
 * @property isHost 此快照的觀察者是否為房主。
 * @property isInRoom 此快照的觀察者目前是否身處該房間內。
 */
data class RoomSnapshot(
    val id: Uuid,
    val hostId: Uuid,
    val config: MahjongRuleConfig,
    val playerIds: Set<Uuid>,
    val readyPlayerIds: Set<Uuid>,
    val aiPlayerIds: Set<Uuid>,
    val canStart: Boolean,
    val isHost: Boolean,
    val isInRoom: Boolean,
)

/**
 * 依據觀察者身分，將權威領域模型 [Room] 轉換為對應的 [RoomSnapshot]。
 *
 * @param observerId 觀察此房間的玩家 Uuid，用於計算 [RoomSnapshot.isHost] 與 [RoomSnapshot.isInRoom]。
 * @return 針對該觀察者視角產生的房間快照。
 */
fun Room.toSnapshot(observerId: Uuid): RoomSnapshot = RoomSnapshot(
    id = id,
    hostId = hostId,
    config = config,
    playerIds = playerIds,
    readyPlayerIds = readyPlayerIds,
    aiPlayerIds = aiPlayerIds,
    canStart = canStart,
    isHost = observerId == hostId,
    isInRoom = playerIds.contains(observerId),
)
