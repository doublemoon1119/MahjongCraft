package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import kotlin.uuid.Uuid

/**
 * 在單一執行環境中執行擴充命令。
 *
 * 第一次執行命令時，才以 [context] 建立 [registry] 中所有命令的 handler，之後重複使用。
 *
 * @property registry 擴充命令的 handler 建立方式。
 * @property context 這個執行環境的對局流程服務。
 */
class ExtensionGameCommandExecutor(
    private val registry: ExtensionGameCommandExecutorRegistry,
    private val context: ExtensionGameCommandContext,
) {
    /** 依命令具體型別索引、綁定 [context] 的 handler。 */
    private val handlers by lazy { registry.createHandlers(context) }

    /**
     * 執行已註冊命令；未知命令回傳不支援。
     *
     * @param gameId 對局識別碼。
     * @param playerId 發出命令的玩家識別碼。
     * @param command 欲執行的擴充命令。
     * @return handler 的執行結果；命令型別未註冊時為不支援錯誤。
     */
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
