package doublemoon.mahjongcraft.scheduler.client

import doublemoon.mahjongcraft.client.gui.MahjongScoreSettlementScreen
import doublemoon.mahjongcraft.game.mahjong.riichi.ScoreSettlement
import doublemoon.mahjongcraft.util.delayOnClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen

object ScoreSettleHandler {
    const val defaultTime = 5 //單位:秒

    private lateinit var screen: Screen
    private var jobCountdown: Job? = null

    var time = 0
        private set

    /**
     * 打開 [screen] 讓玩家選擇
     * */
    @Environment(EnvType.CLIENT)
    private fun openScreen(settlement: ScoreSettlement) {
        ClientScheduler.scheduleDelayAction {
            screen = MahjongScoreSettlementScreen(settlement = settlement)
            MinecraftClient.getInstance().openScreen(screen)
        }
    }

    /**
     * 關閉玩家的 [screen],
     * (必須在主線程上呼叫, 否則遊戲準星或滑鼠會失靈)
     * */
    @Environment(EnvType.CLIENT)
    fun closeScreen() {
        CoroutineScope(Dispatchers.Default).launch {
            if (MinecraftClient.getInstance().currentScreen == screen) {
                ClientScheduler.scheduleDelayAction { screen.onClose() }
            }
        }
    }

    @Environment(EnvType.CLIENT)
    fun start(settlement: ScoreSettlement) {
        jobCountdown?.cancel()
        jobCountdown = CoroutineScope(Dispatchers.Default).launch {
            time = defaultTime
            openScreen(settlement = settlement)
            repeat(times = time) {
                delayOnClient(1000)
                time--
                if (time <= 0) closeScreen()
            }
        }
    }
}