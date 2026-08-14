package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 在房間中新增電腦玩家（AI）的應用層用例。
 *
 * 負責處理房主發起的「新增 AI」請求。此用例會產生一個全新的 Uuid 作為 AI 識別碼，
 * 並將其加入房間的成員清單與 AI 清單中，同時預設 AI 為已準備狀態。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property snapshotRepository 房間快照數據倉庫。
 * @property eventPublisher 房間通知服務。
 */
@Factory
class AddAiPlayerUseCase(
    private val roomRepository: RoomRepository,
    private val snapshotRepository: RoomSnapshotRepository,
    @Provided private val eventPublisher: RoomEventPublisher,
) {
    /**
     * 執行新增 AI 玩家邏輯。
     *
     * @param roomId 房間 Uuid。
     * @param operatorId 發起請求的玩家 Uuid（必須為房主）。
     * @param strategyKey 該 AI 玩家使用的策略登記 key；不傳時預設為 [RandomAiStrategy.KEY]。是否
     *        為有效 key 這裡不驗證——交給 `:mahjong-ai` 的 `MahjongAiStrategyRegistry` 在真正決策
     *        時優雅退回預設策略，維持這個 use case 的單純。
     * @return 新增 AI 的結果，成功時包含 [AddAiPlayerResult]，失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: Uuid,
        operatorId: Uuid,
        strategyKey: String? = null,
    ): Outcome<AddAiPlayerResult, RoomError> {
        val resolvedStrategyKey = strategyKey ?: RandomAiStrategy.KEY

        // 1. 以原子方式讀取房間、驗證業務規則並寫回，避免與其他加入/踢出操作產生競態（如人數上限被同時突破）
        val outcome = roomRepository.update(roomId) { room ->
            when {
                room == null -> room to Outcome.Error(RoomError.RoomNotFound(roomId))
                operatorId != room.hostId -> room to Outcome.Error(RoomError.NotHost(operatorId))
                room.isFull -> room to Outcome.Error(RoomError.RoomIsFull(roomId))
                else -> {
                    // 產生 AI 的 Uuid 並更新領域模型
                    val aiId = Uuid.random()
                    val updatedRoom = room.copy(
                        playerIds = room.playerIds + aiId,
                        aiPlayerStrategyKeys = room.aiPlayerStrategyKeys + (aiId to resolvedStrategyKey),
                        readyPlayerIds = room.readyPlayerIds + aiId, // AI 會直接進入準備就緒狀態
                    )
                    updatedRoom to Outcome.Success(aiId to updatedRoom)
                }
            }
        }

        return when (outcome) {
            is Outcome.Error -> outcome
            is Outcome.Success -> {
                val (aiId, updatedRoom) = outcome.value

                // 2. 同步給所有正在觀察的玩家
                val observers = snapshotRepository.getAllObservers(roomId)
                observers.forEach { observerId ->
                    snapshotRepository.setSnapshot(observerId, updatedRoom.toSnapshot(observerId))
                }

                // 3. 通知房間內所有成員
                updatedRoom.playerIds.forEach { memberId ->
                    eventPublisher.publishJoin(
                        roomId = roomId,
                        targetPlayerId = memberId,
                        joinedPlayerId = aiId,
                        reason = JoinReason.Joined,
                    )
                }

                Outcome.Success(AddAiPlayerResult(aiId, resolvedStrategyKey))
            }
        }
    }
}

/**
 * [AddAiPlayerUseCase] 成功新增 AI 玩家後的結果。
 *
 * @property aiId 新產生的 AI 玩家 Uuid。
 * @property strategyKey 該 AI 實際使用的策略登記 key（已套用預設值解析，不會是 `null`）。
 */
data class AddAiPlayerResult(val aiId: Uuid, val strategyKey: String)
