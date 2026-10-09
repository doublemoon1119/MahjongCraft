package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

/**
 * 執行一種規則 extension 命令的 handler。
 *
 * AI 送出的命令只在權威遊戲仍是 AI 決策時的遊戲時才套用（見 [GameRepository.withExpectedGame]），因此 AI 可能送出的命令
 * 必須遵守：對該局最多做一次權威寫入，且事件、呈現與快照同步等副作用都在這次寫入成功之後才發布。寫入前發現局面已改變時，
 * 寫入會以例外中止，此時命令不得已經發布任何副作用；同一個命令寫入該局兩次會被視為違反契約並停止這次推進。
 */
interface ExtensionGameCommandHandler<C : ExtensionGameCommand> {
    /** 執行指定玩家送出的強型別命令。 */
    suspend fun execute(gameId: Uuid, playerId: Uuid, command: C): Outcome<Unit, GameError>
}

/** 以執行環境提供的 [ExtensionGameCommandContext] 建立一種擴充命令的 handler。 */
fun interface ExtensionGameCommandHandlerFactory<C : ExtensionGameCommand> {
    /** 建立綁定 [context] 的 handler；每個執行環境呼叫一次。 */
    fun create(context: ExtensionGameCommandContext): ExtensionGameCommandHandler<C>
}

/**
 * 執行擴充命令時可使用的對局流程服務。
 *
 * 每個執行環境（例如正式遊戲或隔離的無頭對局）各自提供一份，handler 只會改動自己環境的對局。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 依對局設定取得規則模組。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property handSortPreferenceStore 查詢玩家是否啟用自動整理手牌。
 * @property postActionExhaustiveDrawResolverRegistry 動作完成後主動觸發途中流局的判定 registry。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局呈現通知服務。
 */
@Single
class ExtensionGameCommandContext(
    val gameRepository: GameRepository,
    val moduleRegistry: MahjongModuleRegistry,
    val snapshotSynchronizer: GameSnapshotSynchronizer,
    val handSortPreferenceStore: HandSortPreferenceStore,
    val postActionExhaustiveDrawResolverRegistry: PostActionExhaustiveDrawResolverRegistry,
    @Provided val eventPublisher: GameEventPublisher,
    @Provided val presentationPublisher: GamePresentationPublisher,
)

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

/**
 * 在單一執行環境中執行擴充命令。
 *
 * 第一次執行命令時，才以 [context] 建立 [registry] 中所有命令的 handler，之後重複使用。
 *
 * @property registry 擴充命令的 handler 建立方式。
 * @property context 這個執行環境的對局流程服務。
 */
@Single
class ExtensionGameCommandExecutor(
    private val registry: ExtensionGameCommandExecutorRegistry,
    private val context: ExtensionGameCommandContext,
) {
    /** 依命令具體型別索引、綁定 [context] 的 handler。 */
    private val handlers by lazy { registry.createHandlers(context) }

    /** 執行已註冊命令；未知命令安全回傳不支援。 */
    @Suppress("UNCHECKED_CAST")
    suspend fun execute(
        gameId: Uuid,
        playerId: Uuid,
        command: ExtensionGameCommand,
    ): Outcome<Unit, GameError> {
        val handler = handlers[command::class] as? ExtensionGameCommandHandler<ExtensionGameCommand>
            ?: return Outcome.Error(GameError.UnsupportedAction(gameId, playerId))
        return handler.execute(gameId, playerId, command)
    }
}
