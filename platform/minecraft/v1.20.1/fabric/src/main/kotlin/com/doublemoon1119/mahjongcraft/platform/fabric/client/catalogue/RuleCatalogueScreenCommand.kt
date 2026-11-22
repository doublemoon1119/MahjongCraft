package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal

/**
 * 不需要加入房間的客戶端規則一覽指令。
 *
 * @property screens 延後開啟畫面的入口。
 */
internal class RuleCatalogueScreenCommand(private val screens: RuleCatalogueScreenController) : ClientCommand {
    /**
     * 註冊 `/mahjongcraft_client catalogue screen`。
     *
     * @param events 登記用戶端指令的事件來源。
     */
    fun register(events: ClientEvents) {
        events.onRegisterClientCommand(this)
    }

    override fun <S : Any> build(): LiteralArgumentBuilder<S> = literal<S>(CLIENT_COMMAND_ROOT).then(
        literal<S>(RULE_CATALOGUE).then(
            literal<S>(SCREEN).executes {
                screens.openGeneral()
                1
            },
        ),
    )

    /** 指令節點名稱。 */
    private companion object {
        /** 規則一覽功能節點。 */
        const val RULE_CATALOGUE: String = "catalogue"

        /** 開啟畫面的子節點。 */
        const val SCREEN: String = "screen"
    }
}
