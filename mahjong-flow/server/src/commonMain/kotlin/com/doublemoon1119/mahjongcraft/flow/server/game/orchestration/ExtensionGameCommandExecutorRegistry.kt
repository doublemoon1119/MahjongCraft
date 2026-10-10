package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import kotlin.reflect.KClass

/** 管理擴充命令與其 handler 建立方式的可凍結註冊表。 */
class ExtensionGameCommandExecutorRegistry {
    /** 未擦除型別前的 handler 建立方式包裝。 */
    private class Entry<C : ExtensionGameCommand>(val factory: ExtensionGameCommandHandlerFactory<C>)

    /** 依命令具體型別索引的 handler 建立方式。 */
    private val entries = mutableMapOf<KClass<out ExtensionGameCommand>, Entry<*>>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 目前已登記命令型別的穩定類別名稱快照。 */
    val registrationKeys: Set<String> get() = entries.keys.mapTo(mutableSetOf()) { it.qualifiedName ?: it.toString() }

    /** 註冊一種擴充命令的 handler 建立方式。 */
    fun <C : ExtensionGameCommand> register(
        commandClass: KClass<C>,
        factory: ExtensionGameCommandHandlerFactory<C>,
    ) {
        check(!frozen) { "Extension game command registry is frozen" }
        require(commandClass !in entries) { "Command handler already registered for $commandClass" }
        entries[commandClass] = Entry(factory)
    }

    /** 凍結註冊表。 */
    fun freeze() {
        frozen = true
    }

    /** 查詢指定擴充命令是否已有 handler。 */
    fun isRegistered(commandClass: KClass<out ExtensionGameCommand>): Boolean = commandClass in entries

    /** 以 [context] 建立所有已登記命令的 handler；註冊表必須已凍結，避免之後登記的命令不在結果中。 */
    internal fun createHandlers(context: ExtensionGameCommandContext): Map<KClass<out ExtensionGameCommand>, ExtensionGameCommandHandler<*>> {
        check(frozen) { "Extension game command registry must be frozen before handlers are created" }
        return entries.mapValues { (_, entry) -> entry.factory.create(context) }
    }
}
