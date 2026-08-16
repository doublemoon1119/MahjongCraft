package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimeoutService
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

/**
 * 以 Minecraft server tick 週期觸發權威決策逾時、自動操作心跳與時間同步的 Fabric adapter。
 *
 * @property appScope 將工作綁定目前 server session。
 * @property dispatchers 確保計時工作在 server thread 執行。
 * @property timeoutService 執行與平台無關的逾時政策。
 * @property gameRepository 用於列出所有進行中對局，供每個 tick 的心跳巡邏使用。
 * @property gameFlowCoordinator 對每個進行中對局推進自動操作。
 * @property synchronizationService 每秒同步一次所有真人決策者的權威時間。
 * @property autoDrawService 對每個進行中對局補做真人玩家的自動摸牌檢查。
 */
@Single
class FabricDecisionTimerScheduler(
    private val appScope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val timeoutService: GameDecisionTimeoutService,
    private val gameRepository: GameRepository,
    private val gameFlowCoordinator: GameFlowCoordinator,
    private val synchronizationService: DecisionTimerSynchronizationService,
    private val autoDrawService: MahjongAutoDrawService,
) {
    /** 決策逾時處理錯誤的專用 logger。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 距離上次處理已經過的 server tick 數。 */
    private var elapsedTicks = 0

    /** 避免上一輪尚未完成時重複啟動處理。 */
    private var isProcessing = false

    /** 向 Fabric 登記每秒一次的決策計時處理。 */
    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register {
            elapsedTicks++
            if (elapsedTicks < TICKS_PER_CHECK || isProcessing) return@register
            elapsedTicks = 0
            isProcessing = true
            appScope.launch(dispatchers.main) {
                try {
                    // 先結算逾時：把耗盡思考時間的玩家標記進強制自動操作。
                    timeoutService.processExpiredDecisions()

                    // 每個 tick 把所有進行中的對局都巡一遍，不是只處理這次剛好逾時的對局。原因是：
                    // 玩家一旦被標記成強制自動操作，或整桌都是 AI，就不會再產生任何逾時事件——
                    // 這種桌子如果只靠逾時事件觸發，會永遠沒人叫它繼續走。逐桌巡邏才能保證不管哪種
                    // 情況都會被推進。桌子如果本來就沒事要做，這兩個呼叫很快就會發現沒事然後返回，
                    // 不用擔心多跑這一輪很浪費。
                    gameRepository.getAllGameIds().forEach { gameId ->
                        gameFlowCoordinator.driveAutomatedPlayers(gameId)
                        autoDrawService.checkAndAutoDraw(gameId)
                    }

                    synchronizationService.synchronizeAll()
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
        /** Minecraft 正常運行時每秒的 server tick 數。 */
        const val TICKS_PER_CHECK = 20
    }
}
