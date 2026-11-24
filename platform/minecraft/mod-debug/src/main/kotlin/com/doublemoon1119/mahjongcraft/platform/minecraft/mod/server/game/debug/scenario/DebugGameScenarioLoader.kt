package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.BeginRuleDecisionUseCase
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.GameEventExclusions
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.TablePresentationBusyTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.scenario.DebugGameScenario
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.scenario.DebugGameScenarioContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.scenario.DebugGameScenarioLoadResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.scenario.DebugGameScenarioPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.scenario.DebugGameScenarioResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.scenario.continueAutomaticControls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * 驗證、原子替換並同步 development-only 權威對局情境；載入情境的場次不再產生進度、統計、事件掛勾與歷史紀錄。
 *
 * 牌桌正在播演出或對局在換局途中時，等到可以換上情境再載入，等待有上限。
 *
 * 以完整開局呈現的情境（[DebugGameScenarioPresentation.InitialRound]）與正式開局相同，發牌呈現後開啟局首的規則選擇，
 * 例如台灣麻將的補花。
 *
 * @property minecraftEnvironment 判斷是否允許使用開發情境。
 * @property registry 查詢可載入的情境。
 * @property validator 驗證情境產生的權威狀態。
 * @property membershipRepository 查詢玩家所在的牌桌。
 * @property gameRepository 原子更新目前對局。
 * @property busyTracker 查詢牌桌呈現是否仍在執行。
 * @property presentationSynchronizer 同步情境的桌面呈現。
 * @property decisionAvailabilityService 更新可操作狀態與計時。
 * @property snapshotSynchronizer 廣播更新後的對局快照。
 * @property exclusions 成就與事件掛勾共用的 debug 場次排除名單。
 * @property stateStore 停止情境場次的歷史記錄。
 * @property dispatchers 提供平台呈現使用的執行緒。
 * @property moduleRegistry 取得情境載入後使用的規則模組。
 * @property gameConfigPresentationRegistry 依情境載入後的規則整理流程設定，例如規則選擇時間。
 * @property beginRuleDecisionUseCase 完整開局情境呈現後開啟局首的規則選擇。
 * @property componentRegistry 交給情境初始化規則組件。
 */
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
    private val exclusions: GameEventExclusions,
    private val stateStore: AuthoritativeStateStore,
    private val dispatchers: CoroutineDispatchers,
    private val moduleRegistry: MahjongModuleRegistry,
    private val gameConfigPresentationRegistry: GameConfigPresentationRegistry,
    private val beginRuleDecisionUseCase: BeginRuleDecisionUseCase,
    private val componentRegistry: ComponentRegistry,
) {
    /**
     * 將情境套用到玩家目前所在的進行中遊戲。
     *
     * @param playerId 發起指令的玩家識別碼。
     * @param scenarioId 已登記的開發情境識別碼。
     * @return 載入成功或拒絕原因；呈現失敗時權威替換可能已完成，不會回復原局面。
     */
    suspend fun load(playerId: Uuid, scenarioId: String): DebugGameScenarioLoadResult = loadAndRepeatPresentation(playerId, scenarioId, presentationCount = 1)

    /**
     * 載入情境並連續重建正式桌面呈現，以重現生命週期問題。
     *
     * @param playerId 發起指令的玩家識別碼。
     * @param scenarioId 已登記的開發情境識別碼。
     * @param presentationCount 重建呈現的次數，必須為正數。
     * @return 載入成功或拒絕原因；失敗不保證權威狀態尚未變更。
     * @throws IllegalArgumentException 次數不是正數時拋出。
     */
    suspend fun stress(playerId: Uuid, scenarioId: String, presentationCount: Int): DebugGameScenarioLoadResult {
        require(presentationCount > 0) { "Presentation count must be positive" }
        return loadAndRepeatPresentation(playerId, scenarioId, presentationCount)
    }

    /**
     * 共用一般載入與壓力重建的驗證、權威狀態替換及同步流程。
     *
     * @param playerId 發起指令的玩家識別碼。
     * @param scenarioId 已登記的情境識別碼。
     * @param presentationCount 要重建的呈現次數。
     * @return 載入成功或拒絕原因；對局排除標記與已提交狀態不因後續失敗回復。
     */
    private suspend fun loadAndRepeatPresentation(
        playerId: Uuid,
        scenarioId: String,
        presentationCount: Int,
    ): DebugGameScenarioLoadResult {
        if (!minecraftEnvironment.isDevelopment) return DebugGameScenarioLoadResult.Rejected("Not a development environment")
        val scenario = registry.get(scenarioId) ?: return DebugGameScenarioLoadResult.Rejected("Unknown scenario: $scenarioId")
        val tableId = membershipRepository.getVenueId(playerId)
            ?: return DebugGameScenarioLoadResult.Rejected("Player is not in a Mahjong game")
        val loaded = try {
            withTimeoutOrNull(READY_TIMEOUT) { replaceWhenReady(tableId, playerId, scenario) }
                ?: return DebugGameScenarioLoadResult.Rejected("The table did not become ready within $READY_TIMEOUT")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return DebugGameScenarioLoadResult.Rejected(error.message ?: "Scenario validation failed")
        }
        stateStore.stopHistoryRecording(loaded.game.matchId)

        try {
            repeat(presentationCount) { iteration ->
                if (iteration > 0) awaitPresentationAvailable(tableId)
                withContext(dispatchers.main) { presentationSynchronizer.synchronize(loaded) }
            }
            if (presentationCount > 1) awaitPresentationAvailable(tableId)
            if (loaded.presentation is DebugGameScenarioPresentation.InitialRound) {
                beginRuleDecisionUseCase(tableId, RuleDecisionTiming.ROUND_START)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return DebugGameScenarioLoadResult.Rejected(error.message ?: "Scenario presentation failed")
        }
        decisionAvailabilityService.reconcile(tableId, completedPlayerId = playerId)
        snapshotSynchronizer.syncAll(tableId)
        return DebugGameScenarioLoadResult.Success(scenarioId, presentationCount)
    }

    /**
     * 等牌桌沒有在播演出、對局也不在換局途中，在同一次權威更新內確認這些條件並換上情境；對局會在等待期間繼續進行，
     * 所以條件在更新內重新確認，不成立時繼續等。
     *
     * @param tableId 牌桌識別碼。
     * @param playerId 發起指令的玩家識別碼。
     * @param scenario 要載入的情境。
     * @return 已寫入的情境結果。
     * @throws IllegalArgumentException 沒有進行中的對局、對局已結束或情境驗證失敗時拋出。
     */
    private suspend fun replaceWhenReady(
        tableId: Uuid,
        playerId: Uuid,
        scenario: DebugGameScenario,
    ): DebugGameScenarioResult {
        while (true) {
            while (busyTracker.isBusy(tableId)) delay(PRESENTATION_POLL_INTERVAL)
            val loaded = gameRepository.updateGame(tableId) { currentGame ->
                requireNotNull(currentGame) { "The table does not have a running game" }
                require(!currentGame.isMatchOver) { "The game has already ended" }
                if (currentGame.pendingTransition != null || busyTracker.isBusy(tableId)) return@updateGame currentGame to null
                exclusions.exclude(currentGame.matchId)
                val context = DebugGameScenarioContext(currentGame, playerId, componentRegistry)
                val result = scenario.build(context).continueAutomaticControls(currentGame).withRuleFlowConfig()
                validator.validate(context, result)
                result.game to result
            }
            if (loaded != null) return loaded
            delay(PRESENTATION_POLL_INTERVAL)
        }
    }

    /**
     * 讓流程設定符合情境載入後的規則；例如規則需要玩家選擇時補上規則選擇時間，不需要時清除。
     *
     * @receiver 情境建立的結果。
     * @return 流程設定已整理的結果。
     */
    private fun DebugGameScenarioResult.withRuleFlowConfig(): DebugGameScenarioResult {
        val ruleConfig = game.tableState.config
        val flowConfig = gameConfigPresentationRegistry.normalizeFlowConfig(
            ruleModuleId = moduleRegistry.getModule(ruleConfig).id,
            ruleConfig = ruleConfig,
            current = game.flowConfig,
        )
        return copy(game = game.copy(flowConfig = flowConfig))
    }

    /**
     * 等待上一輪桌面呈現解除忙碌，最多等待設定的上限。
     *
     * @param tableId 要等待的牌桌識別碼。
     * @throws kotlinx.coroutines.TimeoutCancellationException 超過呈現等待上限時拋出。
     */
    private suspend fun awaitPresentationAvailable(tableId: Uuid) {
        withTimeout(PRESENTATION_TIMEOUT) {
            while (busyTracker.isBusy(tableId)) delay(PRESENTATION_POLL_INTERVAL)
        }
    }

    /** 等待牌桌與呈現的上限與檢查間隔。 */
    private companion object {
        /** 等牌桌可以換上情境的上限。 */
        val READY_TIMEOUT: Duration = 2.minutes

        /** 每輪等待桌面呈現完成的上限。 */
        val PRESENTATION_TIMEOUT: Duration = 30.seconds

        /** Busy 狀態的輪詢間隔。 */
        val PRESENTATION_POLL_INTERVAL: Duration = 50.milliseconds
    }
}
