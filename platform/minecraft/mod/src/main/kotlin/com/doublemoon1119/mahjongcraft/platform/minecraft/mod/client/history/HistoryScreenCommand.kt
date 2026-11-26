package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.CLIENT_COMMAND_ROOT
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument

/**
 * 不需要房間或座位的 client 歷史畫面指令。
 *
 * @property screens 延後至下一 tick 開啟的共用入口。
 * @property chatEntries 目前連線的聊天入口識別。
 */
internal class HistoryScreenCommand(private val screens: HistoryScreenController, private val chatEntries: ClientHistoryChatEntryStore) : ClientCommand {
    /**
     * 註冊一般歷史畫面指令及使用本地入口識別碼的聊天點擊指令。
     *
     * @param events 登記用戶端指令的事件來源。
     */
    fun register(events: ClientEvents) {
        events.onRegisterClientCommand(this)
    }

    override fun <S : Any> build(): LiteralArgumentBuilder<S> = literal<S>(CLIENT_COMMAND_ROOT).then(
        literal<S>(HISTORY).then(
            literal<S>(SCREEN).executes {
                screens.openFromCommand()
                1
            },
        ).then(
            literal<S>(ClientHistoryChatEntryStore.CHAT_NODE).then(
                argument<S, String>(TOKEN, StringArgumentType.word()).executes { context ->
                    if (chatEntries.open(StringArgumentType.getString(context, TOKEN), screens::openFromMatchResult)) 1 else 0
                },
            ),
        ),
    )

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
