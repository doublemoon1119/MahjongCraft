package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/**
 * 同步對局快照的應用層用例。
 *
 * 負責處理特定觀察者的同步請求，從權威數據倉庫獲取對局狀態，並針對請求者的身分生成對應的視角快照。
 * 結構完全比照 [com.doublemoon1119.mahjongcraft.flow.server.room.usecase.SyncRoomSnapshotUseCase]。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property gameSnapshotRepository 對局快照數據倉庫。
 */
@Factory
class SyncGameSnapshotUseCase(
    private val gameRepository: GameRepository,
    private val gameSnapshotRepository: GameSnapshotRepository,
) {
    /**
     * 執行對局狀態的同步處理。
     *
     * 根據對局識別碼尋找權威實體，若存在則產出針對指定觀察者的快照並存入快照倉庫。
     *
     * @param gameId 欲同步的對局 Uuid。
     * @param observerId 需要更新快照的觀察者 Uuid。
     * @return 同步快照的結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, observerId: Uuid): Outcome<Unit, GameError> {
        val state = gameRepository.getTableState(gameId)
            ?: return Outcome.Error(GameError.GameNotFound(gameId))

        gameSnapshotRepository.setSnapshot(observerId, state.toSnapshot(observerId))

        return Outcome.Success(Unit)
    }
}
