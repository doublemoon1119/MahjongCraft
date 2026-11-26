package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import kotlin.reflect.KClass

/**
 * 將擴充動作型別綁定到命令建立器，收到該動作時才建立對應命令。
 *
 * @param A 擴充動作的具體型別。
 * @property actionClass 擴充動作的具體型別。
 * @property factory 將動作轉為命令的 factory。
 */
data class ExtensionGameActionCommandFactoryDefinition<A : ExtensionGameAction>(
    val actionClass: KClass<A>,
    val factory: ExtensionGameActionCommandFactory<A>,
)
