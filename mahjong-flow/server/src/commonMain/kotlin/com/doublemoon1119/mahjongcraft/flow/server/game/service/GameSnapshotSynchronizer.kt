package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 統一套用觀看政策並更新 observer-specific 遊戲快照。 */
@Single
class GameSnapshotSynchronizer(
    private val gameRepository: GameRepository,
    private val snapshotRepository: GameSnapshotRepository,
    private val visibilityPolicy: GameVisibilityPolicy,
) {
    /**
     * 同步指定觀看者的快照。
     *
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

    /** 對目前已登記的所有觀看者重新套用觀看政策並同步快照。 */
    suspend fun syncAll(gameId: Uuid) {
        snapshotRepository.getAllObservers(gameId).forEach { observerId -> sync(gameId, observerId) }
    }
}
