package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.text.historyStatusMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.text.prefixedConfigMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftConfigCommandKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.koin.core.annotation.Single

/** 註冊供 OP 查詢與恢復歷史資料庫連線的正式管理員指令。 */
@Single
class FabricHistoryCommand(
    private val writer: FabricHistoryOutboxWriter,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) {
    /** 將 `/mahjongcraft history status|retry` 加入 Fabric command dispatcher。 */
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal(MinecraftModMetadata.MOD_ID)
                    .then(
                        literal(HISTORY_SUBCOMMAND)
                            .requires { source -> source.hasPermissionLevel(REQUIRED_PERMISSION_LEVEL) }
                            .then(literal(STATUS_SUBCOMMAND).executes { context -> status(context.source) })
                            .then(literal(RETRY_SUBCOMMAND).executes { context -> retry(context.source) }),
                    ),
            )
        }
    }

    /** 非同步讀取 writer 狀態，避免在伺服器命令執行緒進行資料庫 I/O。 */
    private fun status(source: ServerCommandSource): Int {
        scope.launch {
            val status = writer.status()
            withContext(dispatchers.main) {
                source.sendFeedback(
                    { historyStatusMessage(status) },
                    false,
                )
            }
        }
        return COMMAND_SUCCESS
    }

    /** 非同步要求 writer 重試目前世界的歷史資料庫連線。 */
    private fun retry(source: ServerCommandSource): Int {
        scope.launch {
            val result = writer.retry(source.server)
            withContext(dispatchers.main) {
                when (result) {
                    HistoryRetryResult.Reconnected -> source.sendFeedback(
                        { prefixedConfigMessage(Text.translatable(MinecraftConfigCommandKeys.HISTORY_RECONNECTED), Formatting.GREEN) },
                        false,
                    )

                    HistoryRetryResult.AlreadyConnected -> source.sendFeedback(
                        { prefixedConfigMessage(Text.translatable(MinecraftConfigCommandKeys.HISTORY_ALREADY_CONNECTED), Formatting.YELLOW) },
                        false,
                    )

                    HistoryRetryResult.Failed -> source.sendError(
                        prefixedConfigMessage(Text.translatable(MinecraftConfigCommandKeys.HISTORY_RECONNECT_FAILED), Formatting.RED),
                    )
                }
            }
        }
        return COMMAND_SUCCESS
    }

    private companion object {
        /** Minecraft operator level required for history administration. */
        const val REQUIRED_PERMISSION_LEVEL: Int = 2

        /** Brigadier success return value. */
        const val COMMAND_SUCCESS: Int = 1

        /** Root history command name. */
        const val HISTORY_SUBCOMMAND: String = "history"

        /** History status subcommand name. */
        const val STATUS_SUBCOMMAND: String = "status"

        /** History reconnect subcommand name. */
        const val RETRY_SUBCOMMAND: String = "retry"
    }
}
