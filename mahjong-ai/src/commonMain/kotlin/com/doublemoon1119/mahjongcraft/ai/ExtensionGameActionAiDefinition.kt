package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import kotlin.reflect.KClass

/**
 * 將擴充動作型別綁定到 AI 處理器，讓登記表能找出該動作的命令候選。
 *
 * @param A 擴充動作的具體型別。
 * @property actionClass 擴充動作的具體型別。
 * @property handler 建立該動作命令候選的 handler。
 */
data class ExtensionGameActionAiDefinition<A : ExtensionGameAction>(
    val actionClass: KClass<A>,
    val handler: ExtensionGameActionAiHandler<A>,
)
