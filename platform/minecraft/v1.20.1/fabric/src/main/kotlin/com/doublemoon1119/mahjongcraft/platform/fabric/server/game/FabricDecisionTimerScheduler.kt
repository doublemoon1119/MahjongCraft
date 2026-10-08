package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimeoutService
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.koin.core.annotation.Single

/**
 * 以 Minecraft server tick 驅動權威決策逾時、自動操作心跳與時間同步的 Fabric adapter。
 *
 * 各對局的自動推進經由 [GameAdvanceRotation] 分散到一秒內的各個 tick：每局每秒推進一次，但不同對局在不同 tick 推進，
 * 避免所有對局擠在同一個 tick。暫停 busy 桌、決策逾時與倒數同步仍是每秒一次的全域處理，順序與推進前相同：先暫停所有
 * busy 桌，再判定逾時，最後同步倒數。逾時已推進過的對局不在同一輪重複推進。每個 tick 的處理見 [DecisionTickProcessor]。
 *
 * 每局推進前只檢查呈現是否忙碌，不另外重新計時或同步倒數，因此每局每秒的權威狀態更新與倒數封包次數與推進前相同。
 *
 * @property appScope 將工作綁定目前 server session。
 * @property dispatchers 確保計時工作在 server thread 執行。
 * @param availabilityService 在逾時判定前先暫停 busy 桌，並在閒置後恢復決策。
 * @param timeoutService 執行與平台無關的逾時政策。
 * @param gameRepository 用於列出所有進行中對局，供心跳巡邏使用。
 * @param gameFlowCoordinator 推進各對局的自動操作。
 * @param synchronizationService 每秒同步一次所有真人決策者的權威時間。
 * @param autoDrawService 補做真人玩家的自動摸牌檢查。
 * @param busyGate 判斷對局是否仍在播放呈現；播放中的對局這一輪不推進。
 */
@Single
class FabricDecisionTimerScheduler(
    private val appScope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    availabilityService: GameDecisionAvailabilityService,
    timeoutService: GameDecisionTimeoutService,
    gameRepository: GameRepository,
    gameFlowCoordinator: GameFlowCoordinator,
    synchronizationService: DecisionTimerSynchronizationService,
    autoDrawService: MahjongAutoDrawService,
    busyGate: GamePresentationBusyGate,
) {
    /** 決策逾時處理錯誤的專用 logger。 */
    private val logger = mahjongCraftLogger(FabricDecisionTimerScheduler::class)

    /** 每個 tick 的推進與每秒一次的全域處理。 */
    private val processor = DecisionTickProcessor(
        rotation = GameAdvanceRotation(TICKS_PER_SECOND),
        listGames = { gameRepository.getAllGameIds() },
        reconcile = { gameId -> availabilityService.reconcile(gameId) },
        settleTimeouts = { timeoutService.processExpiredDecisions() },
        isBusy = busyGate::isBusy,
        resumeTransition = { gameId -> gameFlowCoordinator.resumePendingGameTransition(gameId) },
        drive = { gameId -> gameFlowCoordinator.driveAutomatedPlayers(gameId) },
        autoDraw = { gameId -> autoDrawService.checkAndAutoDraw(gameId) },
        synchronizeAll = { synchronizationService.synchronizeAll() },
        onAdvanceFailed = { gameId, error -> logger.error("Failed to advance automated players for game {}", gameId, error) },
    )

    /** 避免上一輪尚未完成時重複啟動處理；處理中的 tick 只標記待推進。 */
    private var isProcessing = false

    /** 向 Fabric 登記每 tick 的推進與每秒一次的決策計時處理。 */
    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register {
            processor.advanceTick()
            if (isProcessing) return@register
            isProcessing = true
            appScope.launch(dispatchers.main) {
                try {
                    processor.process()
                } catch (throwable: Throwable) {
                    // 這裡是逾時驅動 AI／強制自動操作的入口：沒有真人送出封包時，就靠這個 tick
                    // 迴圈推進對局。任何未預期的例外若不攔截，會讓協程直接死掉且不留下任何 log，
                    // 使對局卡在半途、玩家永遠等不到輪到自己——攔下來記錄，之後至少下個 tick 還能
                    // 重試，不會整場卡死。
                    logger.error("Failed to process expired decisions or synchronize timers", throwable)
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    private companion object {
        /** Minecraft 正常運行時每秒的 server tick 數；每局每秒推進一次，全域處理每秒一次。 */
        const val TICKS_PER_SECOND = 20
    }
}
