package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import org.koin.core.annotation.Single

/**
 * 不需要房間或座位的 client 歷史畫面指令。
 *
 * @property screens 延後至下一 tick 開啟的共用入口。
 * @property chatEntries 目前連線的聊天入口識別。
 */
@Single
internal class FabricHistoryScreenCommand(private val screens: HistoryScreenController, private val chatEntries: ClientHistoryChatEntryStore) {
    /** 註冊 `/mahjongcraft_client history screen`。 */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                literal(CLIENT_COMMAND_ROOT).then(
                    literal(HISTORY).then(
                        literal(SCREEN).executes {
                            screens.openFromCommand()
                            1
                        },
                    ).then(
                        literal(ClientHistoryChatEntryStore.CHAT_NODE).then(
                            argument(TOKEN, StringArgumentType.word()).executes { context ->
                                if (chatEntries.open(StringArgumentType.getString(context, TOKEN), screens::openFromMatchResult)) 1 else 0
                            },
                        ),
                    ),
                ),
            )
        }
    }

    /** 指令節點名稱。 */
    private companion object {
        /** 歷史功能節點。 */
        const val HISTORY = "history"

        /** 開啟畫面節點。 */
        const val SCREEN = "screen"

        /** 本地聊天入口識別參數。 */
        const val TOKEN = "token"
    }
}
