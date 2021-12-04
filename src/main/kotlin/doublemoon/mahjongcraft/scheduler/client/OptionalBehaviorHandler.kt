package doublemoon.mahjongcraft.scheduler.client

import doublemoon.mahjongcraft.client.gui.MahjongGameBehaviorScreen
import doublemoon.mahjongcraft.game.mahjong.riichi.ClaimTarget
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongGameBehavior
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen

/**
 * 將資料顯示在 [MahjongGameBehaviorScreen] 供玩家點擊
 * */
@Environment(EnvType.CLIENT)
object OptionalBehaviorHandler {

    private val client = MinecraftClient.getInstance()
    private var screen: Screen? = null

    private lateinit var behavior: MahjongGameBehavior
    private lateinit var hands: List<MahjongTile>
    private lateinit var target: ClaimTarget
    private lateinit var extraData: String

    var waiting: Boolean = false
        private set

    /**
     * 打開 [screen] 讓玩家選擇
     * */
    fun setScreen() {
        ClientScheduler.scheduleDelayAction {
            screen = MahjongGameBehaviorScreen(behavior, hands, target, extraData)
            client.setScreen(screen!!)
        }
    }

    /**
     * 關閉玩家的 [screen],
     * (必須在主線程上呼叫, 否則遊戲準星或滑鼠會失靈)
     * */
    private fun closeScreen() {
        CoroutineScope(Dispatchers.Default).launch {
            val nowScreen = screen
            if (nowScreen != null && client.currentScreen == nowScreen) {
                ClientScheduler.scheduleDelayAction { nowScreen.onClose() }
            }
        }
    }

    /**
     * @param hands 輸入進來的時候是整數列表
     * */
    fun start(
        behavior: MahjongGameBehavior,
        hands: List<MahjongTile>,
        target: ClaimTarget,
        extraData: String
    ) {
        waiting = true
        this.behavior = behavior
        this.hands = hands
        this.target = target
        this.extraData = extraData
        setScreen()
    }

    fun cancel() {
        waiting = false
        closeScreen()
    }
}