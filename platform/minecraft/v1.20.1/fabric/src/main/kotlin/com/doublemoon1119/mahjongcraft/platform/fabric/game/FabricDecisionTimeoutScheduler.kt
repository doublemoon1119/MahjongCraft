package com.doublemoon1119.mahjongcraft.platform.fabric.game

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimeoutService
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.koin.core.annotation.Single

/**
 * 以 Minecraft server tick 週期觸發權威決策逾時處理的 Fabric adapter。
 *
 * @property appScope 將工作綁定目前 server session。
 * @property dispatchers 確保逾時處理在 server thread 執行。
 * @property timeoutService 執行與平台無關的逾時政策。
 */
@Single
class FabricDecisionTimeoutScheduler(
    private val appScope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val timeoutService: GameDecisionTimeoutService,
) {
    /** 距離上次檢查已經過的 server tick 數。 */
    private var elapsedTicks = 0

    /** 避免上一輪尚未完成時重複啟動處理。 */
    private var isProcessing = false

    /** 向 Fabric 登記每秒一次的決策逾時檢查。 */
    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register {
            elapsedTicks++
            if (elapsedTicks < TICKS_PER_CHECK || isProcessing) return@register
            elapsedTicks = 0
            isProcessing = true
            appScope.launch(dispatchers.main) {
                try {
                    timeoutService.processExpiredDecisions()
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
