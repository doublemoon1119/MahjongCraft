package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/** 在呈現層閒置後，收斂不需要玩家輸入的開局準備步驟。 */
@Factory
class AdvanceAutomaticRoundPreparationUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: RoundPreparationResolverRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
) {
    /**
     * 收斂目前連續的自動步驟。
     *
     * @param gameId 對局 Uuid。
     * @return 權威 preparation 或桌況是否實際改變。
     */
    suspend operator fun invoke(gameId: Uuid): Boolean {
        val changed = gameRepository.updateGame(gameId) { game ->
            val preparation = game?.pendingRoundPreparation
                ?: return@updateGame game to false
            if (preparation.participantPlayerIds.isNotEmpty()) return@updateGame game to false
            val module = moduleRegistry.getModule(game.tableState.config)
            val resolver = resolverRegistry.find(module.id) ?: return@updateGame game to false
            val updated = resolveAutomaticSteps(game, resolver, module)
            updated to (updated != game)
        }
        if (changed) snapshotSynchronizer.syncAll(gameId)
        return changed
    }
}
