package com.doublemoon1119.mahjongcraft.platform.fabric.client.handbook

import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback

/**
 * 開啟麻將手冊快捷畫面的客戶端指令。
 *
 * @property screens 開啟快捷畫面的入口。
 */
internal class FabricHandbookScreenCommand(private val screens: MahjongHandbookScreenController) {
    /** 註冊 `/mahjongcraft_client handbook screen`。 */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                literal(CLIENT_COMMAND_ROOT).then(
                    literal(HANDBOOK).then(
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
        /** 麻將手冊功能節點。 */
        const val HANDBOOK: String = "handbook"

        /** 開啟畫面的子節點。 */
        const val SCREEN: String = "screen"
    }
}
