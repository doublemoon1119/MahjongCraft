package doublemoon.mahjongcraft.scheduler.client

import doublemoon.mahjongcraft.client.gui.YakuSettlementScreen
import doublemoon.mahjongcraft.game.mahjong.riichi.model.YakuSettlement
import doublemoon.mahjongcraft.util.delayOnClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen

object YakuSettleHandler {
    const val defaultTime = 10 //單位:秒

    private val client = MinecraftClient.getInstance()
    private lateinit var screen: Screen
    private var jobCountdown: Job? = null

    var time = 0
        private set

    /**
     * 打開 [screen] 讓玩家選擇
     * */
    @Environment(EnvType.CLIENT)
    private fun setScreen(settlements: List<YakuSettlement>) {
        ClientScheduler.scheduleDelayAction {
            screen = YakuSettlementScreen(settlements = settlements)
            client.setScreen(screen)
        }
    }

    /**
     * 關閉玩家的 [screen],
     * (必須在主線程上呼叫, 否則遊戲準星或滑鼠會失靈)
     * */
    @Environment(EnvType.CLIENT)
    private fun closeScreen() {
        CoroutineScope(Dispatchers.Default).launch {
            if (client.currentScreen == screen) {
                ClientScheduler.scheduleDelayAction { screen.close() }
            }
        }
    }

    /**
     * 時間由要顯示的 [settlementList] 數量決定
     * */
    @Environment(EnvType.CLIENT)
    fun start(settlementList: List<YakuSettlement>) {
        jobCountdown?.cancel()
        jobCountdown = CoroutineScope(Dispatchers.Default).launch {
            time = defaultTime * settlementList.size
            setScreen(settlements = settlementList)
            repeat(times = time) {
                delayOnClient(1000)
                time--
                if (time <= 0) closeScreen()
            }
        }
    }
}