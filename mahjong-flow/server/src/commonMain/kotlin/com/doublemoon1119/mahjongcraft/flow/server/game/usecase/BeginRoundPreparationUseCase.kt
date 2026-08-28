package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/** 發牌後依規則建立第一個開局準備步驟。 */
@Factory
class BeginRoundPreparationUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: RoundPreparationResolverRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
) {
    /** 建立指定對局的第一個準備步驟；規則沒有步驟時維持 null。 */
    suspend operator fun invoke(gameId: Uuid): Outcome<Unit, GameError> {
        val result = gameRepository.updateGame(gameId) { game ->
            if (game == null) return@updateGame null to Outcome.Error(GameError.GameNotFound(gameId))
            if (game.pendingRoundPreparation != null) return@updateGame game to Outcome.Success(Unit)
            val module = moduleRegistry.getModule(game.tableState.config)
            val resolver = resolverRegistry.find(module.id)
                ?: return@updateGame game to Outcome.Success(Unit)
            val firstStep = resolver.begin(game.tableState, module)
            game.copy(pendingRoundPreparation = firstStep) to Outcome.Success(Unit)
        }
        if (result is Outcome.Success) snapshotSynchronizer.syncAll(gameId)
        return result
    }
}
