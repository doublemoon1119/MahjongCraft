package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand

/**
 * 以執行環境提供的 [ExtensionGameCommandContext] 建立一種擴充命令的 handler。
 *
 * @param C 此 factory 建立 handler 所接受的命令型別。
 */
fun interface ExtensionGameCommandHandlerFactory<C : ExtensionGameCommand> {
    /**
     * 建立綁定 [context] 的 handler；每個執行環境呼叫一次。
     *
     * @param context 執行環境提供的命令處理情境。
     * @return 綁定指定情境的擴充命令處理器。
     */
    fun create(context: ExtensionGameCommandContext): ExtensionGameCommandHandler<C>
}
