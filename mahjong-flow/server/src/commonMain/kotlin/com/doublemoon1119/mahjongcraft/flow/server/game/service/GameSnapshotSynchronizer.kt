package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 統一套用觀看政策並更新各觀看者的遊戲快照。
 *
 * @property gameRepository 讀取權威遊戲狀態。
 * @property snapshotRepository 寫入各觀看者的遊戲與準備流程快照。
 * @property visibilityPolicy 為觀看者裁切可公開的狀態。
 */
@Single
class GameSnapshotSynchronizer(
    private val gameRepository: GameRepository,
    private val snapshotRepository: GameSnapshotRepository,
    private val visibilityPolicy: GameVisibilityPolicy,
) {
    /**
     * 同步指定觀看者的快照。
     *
     * @param gameId 要同步的對局識別碼。
     * @param observerId 接收快照的觀看者識別碼。
     * @return 遊戲不存在時為 false；成功寫入快照時為 true。
     */
    suspend fun sync(gameId: Uuid, observerId: Uuid): Boolean {
        val game = gameRepository.getGame(gameId) ?: return false
        snapshotRepository.setSnapshot(observerId, visibilityPolicy.snapshotFor(game, observerId))
        snapshotRepository.setRoundPreparationSnapshot(
            gameId,
            observerId,
            visibilityPolicy.roundPreparationSnapshotFor(game, observerId),
        )
        return true
    }

    /**
     * 對目前已登記的所有觀看者重新套用觀看政策並同步快照。
     *
     * @param gameId 要同步的對局識別碼。
     */
    suspend fun syncAll(gameId: Uuid) {
        snapshotRepository.getAllObservers(gameId).forEach { observerId -> sync(gameId, observerId) }
    }
}
