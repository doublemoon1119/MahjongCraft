package com.doublemoon1119.mahjongcraft.flow.server.api.registration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandHandlerDefinition

/** 擴充登記擴充命令的處理方式時取得的登記介面；查詢、執行與凍結由 MahjongCraft 進行。 */
interface ExtensionGameCommandExecutorRegistrar {
    /** 目前已登記命令型別的穩定類別名稱快照。 */
    val registrationKeys: Set<String>

    /**
     * 註冊一種擴充命令的 handler 建立方式；註冊表凍結後或同一命令型別已登記時會失敗。
     *
     * @param C 命令型別。
     * @param definition 命令類別與 handler factory。
     */
    fun <C : ExtensionGameCommand> register(definition: ExtensionGameCommandHandlerDefinition<C>)
}
