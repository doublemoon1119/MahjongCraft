package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareRiichiUseCase
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

/** 執行一種規則 extension 命令的 handler。 */
interface ExtensionGameCommandHandler<C : ExtensionGameCommand> {
    /** 執行前是否必須先結算已成立的四槓散了等槓後流局。 */
    val resolvesPendingKanDrawBeforeExecution: Boolean
        get() = false

    /** 執行指定玩家送出的強型別命令。 */
    suspend fun execute(gameId: Uuid, playerId: Uuid, command: C): Outcome<Unit, GameError>
}

/** 管理擴充命令與伺服器 handler 的可凍結註冊表。 */
class ExtensionGameCommandExecutorRegistry {
    /** 未擦除型別前的 handler 包裝。 */
    private class Entry<C : ExtensionGameCommand>(val handler: ExtensionGameCommandHandler<C>)

    /** 依命令具體型別索引的 handler。 */
    private val entries = mutableMapOf<KClass<out ExtensionGameCommand>, Entry<*>>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 註冊一種擴充命令的伺服器 handler。 */
    fun <C : ExtensionGameCommand> register(
        commandClass: KClass<C>,
        handler: ExtensionGameCommandHandler<C>,
    ) {
        check(!frozen) { "Extension game command registry is frozen" }
        require(commandClass !in entries) { "Command handler already registered for $commandClass" }
        entries[commandClass] = Entry(handler)
    }

    /** 凍結註冊表。 */
    fun freeze() {
        frozen = true
    }

    /** 查詢指定擴充命令是否已有伺服器 handler。 */
    fun isRegistered(commandClass: KClass<out ExtensionGameCommand>): Boolean = commandClass in entries

    /** 執行已註冊命令；未知命令安全回傳不支援。 */
    @Suppress("UNCHECKED_CAST")
    suspend fun execute(
        gameId: Uuid,
        playerId: Uuid,
        command: ExtensionGameCommand,
    ): Outcome<Unit, GameError> {
        val entry = entries[command::class] as? Entry<ExtensionGameCommand>
            ?: return Outcome.Error(GameError.UnsupportedAction(gameId, playerId))
        return entry.handler.execute(gameId, playerId, command)
    }

    /** 查詢命令是否要求先結算已成立的槓後流局。 */
    @Suppress("UNCHECKED_CAST")
    fun resolvesPendingKanDrawBeforeExecution(command: ExtensionGameCommand): Boolean {
        val entry = entries[command::class] as? Entry<ExtensionGameCommand> ?: return false
        return entry.handler.resolvesPendingKanDrawBeforeExecution
    }
}

/** 登記 MahjongCraft 內建規則提供的擴充命令 handler。 */
fun ExtensionGameCommandExecutorRegistry.registerRiichiGameCommandHandler(declareRiichiUseCase: DeclareRiichiUseCase) {
    register(
        RiichiGameCommand::class,
        object : ExtensionGameCommandHandler<RiichiGameCommand> {
            override val resolvesPendingKanDrawBeforeExecution: Boolean = true

            override suspend fun execute(
                gameId: Uuid,
                playerId: Uuid,
                command: RiichiGameCommand,
            ): Outcome<Unit, GameError> = declareRiichiUseCase(gameId, playerId, command.tileId)
        },
    )
}
