package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSource
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 自動測試執行的一個伺服器指令的結果與回饋。
 *
 * - 同時作為指令的訊息接收者，收下原本會送到聊天欄的回饋。
 * - 指令可能在執行完之後才送出回饋（例如非同步載入的結果），因此回饋可以在任何執行緒上陸續加入。
 *
 * @property command 執行的指令，不含開頭斜線。
 */
internal class AutotestCommandFeedback(val command: String) : CommandSource {
    /**
     * 指令執行完的回傳值，還沒執行完時為 null。
     *
     * - 回傳值的意義依指令而定。例如設定遊戲規則為 false 時回傳 0，不代表失敗。
     */
    @Volatile
    var result: Int? = null

    /** 成功的回饋訊息，依收到順序。 */
    val successes: MutableList<String> = CopyOnWriteArrayList()

    /** 失敗的回饋訊息，依收到順序。 */
    val failures: MutableList<String> = CopyOnWriteArrayList()

    /** 指令已執行完，而且沒有失敗的回饋。原版的指令出錯時一律送出失敗的回饋。 */
    val succeeded: Boolean
        get() = result != null && failures.isEmpty()

    /** 是否已經收到任何回饋。 */
    val answered: Boolean
        get() = successes.isNotEmpty() || failures.isNotEmpty()

    override fun sendSystemMessage(component: Component) {
        // 原版的失敗回饋是紅色的訊息，成功回饋不指定顏色。
        val isFailure = component.style.color == TextColor.fromLegacyFormat(ChatFormatting.RED)
        (if (isFailure) failures else successes) += component.string
    }

    override fun acceptsSuccess(): Boolean = true

    override fun acceptsFailure(): Boolean = true

    override fun shouldInformAdmins(): Boolean = false
}
