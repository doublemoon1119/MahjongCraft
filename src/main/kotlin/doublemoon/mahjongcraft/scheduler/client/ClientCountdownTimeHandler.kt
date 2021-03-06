package doublemoon.mahjongcraft.scheduler.client

import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

/**
 * 儲存由數據包收到的時間, 沒有任何功能
 * */
object ClientCountdownTimeHandler {

    private val client = MinecraftClient.getInstance()
    private const val titleFadeInTime = 5
    private const val titleRemainTime = 10
    private const val titleFadeOutTime = 5

    /**
     * null to null 表示沒在倒數
     * */
    var basicAndExtraTime: Pair<Int?, Int?> = null to null
        set(value) {
            if (value.first != null && value.second != null) {  //有新的值出現
                displayTime(timeBase = value.first!!, timeExtra = value.second!!)
            } else {  //兩個都為 null, 清除倒數計時, 關閉所有該關的處理程序
                if (OptionalBehaviorHandler.waiting) OptionalBehaviorHandler.cancel()
                client.inGameHud.clearTitle()
            }
            field = value
        }


    /**
     * 顯示剩餘的思考時間
     * */
    private fun displayTime(timeBase: Int, timeExtra: Int) {
        val base = if (timeBase > 0) "§a$timeBase" else ""
        val plus = if (timeBase > 0 && timeExtra > 0) "§e + " else ""
        val extra = if (timeExtra > 0) "§c$timeExtra" else ""
        val text = Text.of("$base$plus$extra")
        //TODO 改位置顯示時間
        with(client.inGameHud) {
            setTitle(Text.of(""))
            setSubtitle(text)
            setTitleTicks(titleFadeInTime, titleRemainTime, titleFadeOutTime)
        }
    }
}