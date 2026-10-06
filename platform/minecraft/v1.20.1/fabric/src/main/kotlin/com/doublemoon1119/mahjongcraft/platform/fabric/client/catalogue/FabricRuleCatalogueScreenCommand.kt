package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import org.koin.core.annotation.Single

/**
 * 不需要加入房間的客戶端規則一覽指令。
 * @property screens 延後開啟畫面的入口。
 */
@Single
internal class FabricRuleCatalogueScreenCommand(private val screens: RuleCatalogueScreenController) {
    /** 註冊 `/mahjongcraft_client catalogue screen`。 */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                literal(CLIENT_COMMAND_ROOT).then(
                    literal(RULE_CATALOGUE).then(
                        literal(SCREEN).executes {
                            screens.openGeneral()
                            1
                        },
                    ),
                ),
            )
        }
    }

    /** 指令節點名稱。 */
    private companion object {
        /** 規則一覽功能節點。 */
        const val RULE_CATALOGUE: String = "catalogue"

        /** 開啟畫面的子節點。 */
        const val SCREEN: String = "screen"
    }
}
