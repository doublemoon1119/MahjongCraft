package com.doublemoon1119.mahjongcraft.platform.fabric.client.guide

import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback

/**
 * 開啟「如何開始」畫面的客戶端指令。
 *
 * @property screens 開啟畫面的入口。
 */
internal class GuideScreenCommand(private val screens: GuideScreenController) {
    /** 註冊 `/mahjongcraft_client guide screen`。 */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                literal(CLIENT_COMMAND_ROOT).then(
                    literal(GUIDE).then(
                        literal(SCREEN).executes {
                            screens.openFromCommand()
                            1
                        },
                    ),
                ),
            )
        }
    }

    /** 指令節點名稱。 */
    private companion object {
        /** 如何開始功能節點。 */
        const val GUIDE: String = "guide"

        /** 開啟畫面的子節點。 */
        const val SCREEN: String = "screen"
    }
}
