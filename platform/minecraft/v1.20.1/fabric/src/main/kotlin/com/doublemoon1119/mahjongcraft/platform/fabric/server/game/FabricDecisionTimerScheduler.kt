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
import kotlin.uuid.Uuid

/**
 * 以 Minecraft server tick 驅動權威決策逾時、自動操作心跳與時間同步的 Fabric adapter。
 *
 * 各對局的自動推進經由 [GameAdvanceRotation] 分散到一秒內的各個 tick：每局每秒推進一次，但不同對局在不同 tick 推進，
 * 避免所有對局擠在同一個 tick。暫停 busy 桌、決策逾時與倒數同步仍是每秒一次的全域處理，順序與推進前相同：先暫停所有
 * busy 桌，再判定逾時，最後同步倒數。逾時已推進過的對局不在同一輪重複推進。
 *
 * 每局推進前只檢查呈現是否忙碌，不另外重新計時或同步倒數，因此每局每秒的權威狀態更新與倒數封包次數與推進前相同。
 *
 * @property appScope 將工作綁定目前 server session。
 * @property dispatchers 確保計時工作在 server thread 執行。
 * @property availabilityService 在逾時判定前先暫停 busy 桌，並在閒置後恢復決策。
 * @property timeoutService 執行與平台無關的逾時政策。
 * @property gameRepository 用於列出所有進行中對局，供心跳巡邏使用。
 * @property gameFlowCoordinator 推進各對局的自動操作。
 * @property synchronizationService 每秒同步一次所有真人決策者的權威時間。
 * @property autoDrawService 補做真人玩家的自動摸牌檢查。
 * @property busyGate 判斷對局是否仍在播放呈現；播放中的對局這一輪不推進。
 */
@Single
class FabricDecisionTimerScheduler(
    private val appScope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val availabilityService: GameDecisionAvailabilityService,
    private val timeoutService: GameDecisionTimeoutService,
    private val gameRepository: GameRepository,
    private val gameFlowCoordinator: GameFlowCoordinator,
    private val synchronizationService: DecisionTimerSynchronizationService,
    private val autoDrawService: MahjongAutoDrawService,
    private val busyGate: GamePresentationBusyGate,
) {
    /** 決策逾時處理錯誤的專用 logger。 */
    private val logger = mahjongCraftLogger(FabricDecisionTimerScheduler::class)

    /** 各對局推進的 tick 位置與待推進標記。 */
    private val rotation = GameAdvanceRotation(TICKS_PER_SECOND)

    /** 避免上一輪尚未完成時重複啟動處理；處理中的 tick 只標記待推進。 */
    private var isProcessing = false

    /** 向 Fabric 登記每 tick 的推進與每秒一次的決策計時處理。 */
    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register {
            rotation.advanceTick()
            if (isProcessing) return@register
            isProcessing = true
            appScope.launch(dispatchers.main) {
                try {
                    val gameIds = gameRepository.getAllGameIds()
                    rotation.syncGames(gameIds)
                    val runGlobal = rotation.globalDue
                    if (runGlobal) {
                        // 必須先暫停所有 busy 桌，才可 claim timeout；否則同一輪剛開始播放動畫的玩家仍可能
                        // 先被判定逾時，之後才輪到 busy 檢查。
                        gameIds.forEach { gameId -> availabilityService.reconcile(gameId) }
                        // 結算真正處於可操作狀態且已耗盡時間的決策；逾時處理已推進這些對局。
                        rotation.markAdvanced(timeoutService.processExpiredDecisions())
                    }

                    // 輪到的對局都巡一遍，不是只處理剛好逾時的對局：玩家一旦被標記成強制自動操作，或整桌都是 AI，
                    // 就不會再產生任何逾時事件，這種桌子只能靠巡邏推進。桌子本來就沒事要做時，這些呼叫很快就會返回。
                    advanceEachGame(
                        gameIds = rotation.takeDue(),
                        advance = ::advanceGame,
                        onFailure = { gameId, error -> logger.error("Failed to advance automated players for game {}", gameId, error) },
                    )

                    if (runGlobal) {
                        synchronizationService.synchronizeAll()
                        rotation.completeGlobal()
                    }
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

    /** 推進一局：呈現播放中時這一輪略過；胡牌或流局結算後的待完成流程先補完，這一輪就不再驅動玩家操作。 */
    private suspend fun advanceGame(gameId: Uuid) {
        if (busyGate.isBusy(gameId)) return
        if (gameFlowCoordinator.resumePendingGameTransition(gameId)) return
        gameFlowCoordinator.driveAutomatedPlayers(gameId)
        autoDrawService.checkAndAutoDraw(gameId)
    }

    private companion object {
        /** Minecraft 正常運行時每秒的 server tick 數；每局每秒推進一次，全域處理每秒一次。 */
        const val TICKS_PER_SECOND = 20
    }
}
