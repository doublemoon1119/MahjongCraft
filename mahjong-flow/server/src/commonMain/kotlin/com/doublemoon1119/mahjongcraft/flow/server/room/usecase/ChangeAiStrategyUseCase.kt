package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import kotlin.uuid.Uuid

/**
 * 替房間內既有 AI 玩家更換策略的應用層用例。
 *
 * 只更新 [Room.aiPlayerStrategyKeys]，不影響
 * 成員清單或準備狀態，因此不需要同步房間快照或發送房間通知——快照模型本來就不包含策略 key，其他
 * 房間成員也無從得知或需要得知 AI 的策略設定。
 *
 * @property roomRepository 權威房間數據倉庫。
 * @property strategyRegistry 確認 AI 策略已登記；未登記的 key 不會寫入房間。
 */
class ChangeAiStrategyUseCase(
    private val roomRepository: RoomRepository,
    private val strategyRegistry: MahjongAiStrategyRegistry,
) {
    /**
     * 執行更換 AI 策略邏輯。
     *
     * @param roomId 房間 Uuid。
     * @param operatorId 發起請求的玩家 Uuid（必須為房主）。
     * @param targetAiId 欲更換策略的 AI 玩家 Uuid，必須是房間內的 AI。
     * @param strategyKey 新的策略登記 key；未登記的 key 以 [RoomError.UnknownAiStrategy] 拒絕。
     * @return 更換結果，成功時為更換前的策略登記 key（供呼叫端組出「舊策略 → 新策略」的呈現），
     *   失敗時為 [RoomError]。
     */
    suspend operator fun invoke(
        roomId: Uuid,
        operatorId: Uuid,
        targetAiId: Uuid,
        strategyKey: String,
    ): Outcome<String, RoomError> = roomRepository.update(roomId) { room ->
        when {
            room == null -> room to Outcome.Error(RoomError.RoomNotFound(roomId))
            operatorId != room.hostId -> room to Outcome.Error(RoomError.NotHost(operatorId))
            targetAiId !in room.playerIds -> room to Outcome.Error(RoomError.PlayerNotInRoom(targetAiId, roomId))
            !room.isAi(targetAiId) -> room to Outcome.Error(RoomError.NotAiPlayer(targetAiId, roomId))
            strategyKey !in strategyRegistry.getAllStrategyKeys() -> room to Outcome.Error(RoomError.UnknownAiStrategy(roomId))
            else -> {
                // isAi(targetAiId) 剛驗證過，aiPlayerStrategyKeys 必定持有這個 key。
                val previousStrategyKey = room.aiPlayerStrategyKeys.getValue(targetAiId)
                val updatedRoom = room.copy(
                    aiPlayerStrategyKeys = room.aiPlayerStrategyKeys + (targetAiId to strategyKey),
                )
                updatedRoom to Outcome.Success(previousStrategyKey)
            }
        }
    }
}
