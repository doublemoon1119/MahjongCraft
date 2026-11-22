package com.doublemoon1119.mahjongcraft.platform.fabric.client.guide

import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal

/**
 * 開啟「如何開始」畫面的客戶端指令。
 *
 * @property screens 開啟畫面的入口。
 */
internal class GuideScreenCommand(private val screens: GuideScreenController) : ClientCommand {
    /**
     * 註冊 `/mahjongcraft_client guide screen`。
     *
     * @param events 登記用戶端指令的事件來源。
     */
    fun register(events: ClientEvents) {
        events.onRegisterClientCommand(this)
    }

    override fun <S : Any> build(): LiteralArgumentBuilder<S> = literal<S>(CLIENT_COMMAND_ROOT).then(
        literal<S>(GUIDE).then(
            literal<S>(SCREEN).executes {
                screens.openFromCommand()
                1
            },
        ),
    )

    /** 指令節點名稱。 */
    private companion object {
        /** 如何開始功能節點。 */
        const val GUIDE: String = "guide"

        /** 開啟畫面的子節點。 */
        const val SCREEN: String = "screen"
    }
}
