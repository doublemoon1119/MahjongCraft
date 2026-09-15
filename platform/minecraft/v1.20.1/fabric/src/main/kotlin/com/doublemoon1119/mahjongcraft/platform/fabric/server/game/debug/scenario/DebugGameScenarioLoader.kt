package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TablePresentationBusyTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 載入 development-only 權威情境時可能回傳的結果。 */
sealed interface DebugGameScenarioLoadResult {
    /** 情境已成功寫入並完成同步。 */
    data class Success(
        /** 已載入的 scenario ID。 */
        val scenarioId: String,
        /** 本次完整重建桌面呈現的次數。 */
        val presentationCount: Int = 1,
    ) : DebugGameScenarioLoadResult

    /** 情境載入遭拒，並攜帶供 debug 指令顯示的原因。 */
    data class Rejected(
        /** 不含玩家資料的拒絕原因。 */
        val reason: String,
    ) : DebugGameScenarioLoadResult
}

/** 驗證、原子替換並同步 development-only 權威對局情境。 */
@Single
class DebugGameScenarioLoader(
    private val minecraftEnvironment: MinecraftEnvironment,
    private val registry: DebugGameScenarioRegistry,
    private val validator: DebugGameScenarioValidator,
    private val membershipRepository: PlayerMembershipRepository,
    private val gameRepository: GameRepository,
    private val busyTracker: TablePresentationBusyTracker,
    private val presentationSynchronizer: DebugGameScenarioPresentationSynchronizer,
    private val decisionAvailabilityService: GameDecisionAvailabilityService,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val dispatchers: CoroutineDispatchers,
) {
    /** 將 [scenarioId] 套用到 [playerId] 目前所在的進行中遊戲。 */
    suspend fun load(playerId: Uuid, scenarioId: String): DebugGameScenarioLoadResult = loadAndRepeatPresentation(playerId, scenarioId, presentationCount = 1)

    /** 載入 [scenarioId]，並連續重建 [presentationCount] 次正式桌面呈現以重現生命週期問題。 */
    suspend fun stress(playerId: Uuid, scenarioId: String, presentationCount: Int): DebugGameScenarioLoadResult {
        require(presentationCount > 0) { "Presentation count must be positive" }
        return loadAndRepeatPresentation(playerId, scenarioId, presentationCount)
    }

    /** 共用一般載入與壓力重建的驗證、權威狀態替換及同步流程。 */
    private suspend fun loadAndRepeatPresentation(
        playerId: Uuid,
        scenarioId: String,
        presentationCount: Int,
    ): DebugGameScenarioLoadResult {
        if (!minecraftEnvironment.isDevelopment) return DebugGameScenarioLoadResult.Rejected("Not a development environment")
        val scenario = registry.get(scenarioId) ?: return DebugGameScenarioLoadResult.Rejected("Unknown scenario: $scenarioId")
        val tableId = membershipRepository.getTableId(playerId)
            ?: return DebugGameScenarioLoadResult.Rejected("Player is not in a Mahjong game")
        if (busyTracker.isBusy(tableId)) return DebugGameScenarioLoadResult.Rejected("Table presentation is still busy")

        val loaded = runCatching {
            gameRepository.updateGame(tableId) { currentGame ->
                requireNotNull(currentGame) { "The table does not have a running game" }
                require(!currentGame.isMatchOver) { "The game has already ended" }
                require(currentGame.pendingTransition == null) { "The game has a pending transition" }
                val context = DebugGameScenarioContext(currentGame, playerId)
                val result = scenario.build(context)
                validator.validate(context, result)
                result.game to result
            }
        }.getOrElse { error -> return DebugGameScenarioLoadResult.Rejected(error.message ?: "Scenario validation failed") }

        runCatching {
            repeat(presentationCount) { iteration ->
                if (iteration > 0) awaitPresentationAvailable(tableId)
                withContext(dispatchers.main) { presentationSynchronizer.synchronize(loaded) }
            }
            if (presentationCount > 1) awaitPresentationAvailable(tableId)
        }.getOrElse { error ->
            return DebugGameScenarioLoadResult.Rejected(error.message ?: "Scenario presentation failed")
        }
        decisionAvailabilityService.reconcile(tableId, completedPlayerId = playerId)
        snapshotSynchronizer.syncAll(tableId)
        return DebugGameScenarioLoadResult.Success(scenarioId, presentationCount)
    }

    /** 等待上一輪 presentation 解除 busy；逾時時保留明確的壓力測試失敗。 */
    private suspend fun awaitPresentationAvailable(tableId: Uuid) {
        withTimeout(PRESENTATION_TIMEOUT_MILLIS) {
            while (busyTracker.isBusy(tableId)) delay(PRESENTATION_POLL_MILLIS)
        }
    }

    private companion object {
        /** 每輪等待桌面呈現完成的上限。 */
        const val PRESENTATION_TIMEOUT_MILLIS: Long = 30_000L

        /** Busy 狀態的輪詢間隔。 */
        const val PRESENTATION_POLL_MILLIS: Long = 50L
    }
}
