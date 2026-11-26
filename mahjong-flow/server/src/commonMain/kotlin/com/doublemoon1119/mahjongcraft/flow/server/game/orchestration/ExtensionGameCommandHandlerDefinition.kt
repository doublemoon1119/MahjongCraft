package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import kotlin.reflect.KClass

/**
 * 將擴充命令型別綁定到處理器建立方式，執行時依命令型別取得處理器。
 *
 * @param C 擴充命令的具體型別。
 * @property commandClass 擴充命令的具體型別。
 * @property factory 建立該命令 handler 的 factory。
 */
data class ExtensionGameCommandHandlerDefinition<C : ExtensionGameCommand>(
    val commandClass: KClass<C>,
    val factory: ExtensionGameCommandHandlerFactory<C>,
)
