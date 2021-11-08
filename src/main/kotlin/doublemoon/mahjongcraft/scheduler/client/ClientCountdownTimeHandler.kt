package doublemoon.mahjongcraft.scheduler.client

import net.minecraft.client.MinecraftClient
import net.minecraft.text.LiteralText

/**
 * 儲存由數據包收到的時間, 沒有任何功能
 * */
object ClientCountdownTimeHandler {

    private const val titleFadeInTime = 5
    private const val titleRemainTime = 10
    private const val titleFadeOutTime = 5
    private val client = MinecraftClient.getInstance()

    /**
     * null to null 表示沒在倒數
     * */
    var basicAndExtraTime: Pair<Int?, Int?> = null to null
        set(value) {
//            logger.info("base: ${value.first}, extra: ${value.second}")
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
        val textBase = if (timeBase > 0) "§a$timeBase" else ""
        val textPlus = if (timeBase > 0 && timeExtra > 0) "§e + " else ""
        val textExtra = if (timeExtra > 0) "§c$timeExtra" else ""
        val text = LiteralText("$textBase$textPlus$textExtra")
        //TODO 改位置顯示時間
        with(client.inGameHud) {
            setSubtitle(text)
            setTitleTicks(titleFadeInTime, titleRemainTime, titleFadeOutTime)
        }
    }
}