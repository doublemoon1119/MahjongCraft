package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.text.historyCleanupPreviewMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.text.historyCleanupReportMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.text.historyStatusMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.text.historyStorageMessage
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

/**
 * 註冊供 OP 查詢與恢復歷史資料庫連線的正式管理員指令。
 *
 * @property writer 目前存檔的歷史 writer 與 session 管理邊界。
 * @property scope 執行非同步管理工作的應用程式協程範圍。
 * @property dispatchers 將資料庫工作與指令回覆分派至適當執行緒的 dispatcher 集合。
 */
@Single
class FabricHistoryCommand(
    private val writer: FabricHistoryOutboxWriter,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) {
    /** 將 `/mahjongcraft history` 管理指令樹加入 Fabric 指令分派器。 */
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal(MinecraftModMetadata.MOD_ID)
                    .then(
                        literal(HISTORY_SUBCOMMAND)
                            .requires { source -> source.hasPermissionLevel(REQUIRED_PERMISSION_LEVEL) }
                            .then(literal(STATUS_SUBCOMMAND).executes { context -> status(context.source) })
                            .then(literal(RETRY_SUBCOMMAND).executes { context -> retry(context.source) })
                            .then(
                                literal(STORAGE_SUBCOMMAND).executes { context -> storage(context.source) },
                            )
                            .then(
                                literal(CLEANUP_SUBCOMMAND)
                                    .then(literal(PREVIEW_SUBCOMMAND).executes { context -> cleanupPreview(context.source) })
                                    .then(literal(RUN_SUBCOMMAND).executes { context -> cleanupRun(context.source) }),
                            ),
                    ),
            )
        }
    }

    /**
     * 非同步讀取 writer 狀態，避免在伺服器命令執行緒進行資料庫 I/O。
     *
     * @param source 已通過 OP 權限檢查的命令來源。
     * @return Brigadier 的成功接收值，不表示非同步工作已完成。
     */
    private fun status(source: ServerCommandSource): Int {
        val session = writer.currentSessionId
        scope.launch {
            if (!writer.isCurrentSession(session)) return@launch
            val status = writer.status()
            withContext(dispatchers.main) {
                if (!writer.isCurrentSession(session)) return@withContext
                source.sendFeedback(
                    { historyStatusMessage(status) },
                    false,
                )
            }
        }
        return COMMAND_SUCCESS
    }

    /**
     * 非同步要求 writer 重試目前世界的歷史資料庫連線。
     *
     * @param source 已通過 OP 權限檢查的命令來源。
     * @return Brigadier 的成功接收值。
     */
    private fun retry(source: ServerCommandSource): Int {
        val session = writer.currentSessionId
        val server = source.server
        scope.launch {
            if (!writer.isCurrentSession(session)) return@launch
            val result = writer.retry(server, session)
            withContext(dispatchers.main) {
                if (!writer.isCurrentSession(session)) return@withContext
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

    /**
     * 非同步產生目前存檔的歷史用量快照。
     *
     * @param source 已通過 OP 權限檢查的命令來源。
     * @return Brigadier 的成功接收值。
     */
    private fun storage(source: ServerCommandSource): Int {
        val session = writer.currentSessionId
        source.sendFeedback({ Text.translatable(MinecraftConfigCommandKeys.HISTORY_STORAGE_STARTED) }, false)
        scope.launch {
            if (!writer.isCurrentSession(session)) return@launch
            val result = writer.storage(session)
            withContext(dispatchers.main) {
                if (!writer.isCurrentSession(session)) return@withContext
                when (result) {
                    is HistoryManagementResult.Success -> source.sendFeedback({ historyStorageMessage(result.value) }, false)
                    is HistoryManagementResult.Busy -> storageFailure(source, MinecraftConfigCommandKeys.HISTORY_MANAGEMENT_BUSY, result.cachedSnapshot)
                    is HistoryManagementResult.Disconnected -> storageFailure(source, MinecraftConfigCommandKeys.HISTORY_MANAGEMENT_DISCONNECTED, result.cachedSnapshot)
                    is HistoryManagementResult.Failed -> storageFailure(source, MinecraftConfigCommandKeys.HISTORY_MANAGEMENT_FAILED, result.cachedSnapshot)
                    HistoryManagementResult.SessionChanged -> Unit
                }
            }
        }
        return COMMAND_SUCCESS
    }

    /**
     * 非同步預覽歷史清理候選，不執行資料庫變更。
     *
     * @param source 已通過 OP 權限檢查的命令來源。
     * @return Brigadier 的成功接收值。
     */
    private fun cleanupPreview(source: ServerCommandSource): Int {
        val session = writer.currentSessionId
        source.sendFeedback({ Text.translatable(MinecraftConfigCommandKeys.HISTORY_PREVIEW_STARTED) }, false)
        scope.launch {
            if (!writer.isCurrentSession(session)) return@launch
            val result = writer.previewCleanup(session)
            withContext(dispatchers.main) {
                if (!writer.isCurrentSession(session)) return@withContext
                when (result) {
                    is HistoryManagementResult.Success -> source.sendFeedback({ historyCleanupPreviewMessage(result.value) }, false)
                    is HistoryManagementResult.Busy -> source.sendError(Text.translatable(MinecraftConfigCommandKeys.HISTORY_MANAGEMENT_BUSY))
                    is HistoryManagementResult.Disconnected -> source.sendError(Text.translatable(MinecraftConfigCommandKeys.HISTORY_MANAGEMENT_DISCONNECTED))
                    is HistoryManagementResult.Failed -> source.sendError(Text.translatable(MinecraftConfigCommandKeys.HISTORY_MANAGEMENT_FAILED))
                    HistoryManagementResult.SessionChanged -> Unit
                }
            }
        }
        return COMMAND_SUCCESS
    }

    /**
     * 非同步執行歷史清理並回報已提交的實際結果。
     *
     * @param source 已通過 OP 權限檢查的命令來源。
     * @return Brigadier 的成功接收值。
     */
    private fun cleanupRun(source: ServerCommandSource): Int {
        val session = writer.currentSessionId
        source.sendFeedback({ Text.translatable(MinecraftConfigCommandKeys.HISTORY_CLEANUP_STARTED) }, false)
        scope.launch {
            if (!writer.isCurrentSession(session)) return@launch
            val result = writer.runCleanup(session)
            withContext(dispatchers.main) {
                if (!writer.isCurrentSession(session)) return@withContext
                when (result) {
                    is HistoryManagementResult.Success -> source.sendFeedback({ historyCleanupReportMessage(result.value) }, false)
                    is HistoryManagementResult.Busy -> source.sendError(Text.translatable(MinecraftConfigCommandKeys.HISTORY_MANAGEMENT_BUSY))
                    is HistoryManagementResult.Disconnected -> source.sendError(Text.translatable(MinecraftConfigCommandKeys.HISTORY_MANAGEMENT_DISCONNECTED))
                    is HistoryManagementResult.Failed -> result.cleanup?.let { source.sendFeedback({ historyCleanupReportMessage(it) }, false) }
                        ?: source.sendError(Text.translatable(MinecraftConfigCommandKeys.HISTORY_MANAGEMENT_FAILED))
                    HistoryManagementResult.SessionChanged -> Unit
                }
            }
        }
        return COMMAND_SUCCESS
    }

    /**
     * 明示查詢失敗原因；若有同 session 快照則附上舊資料，不假造目前用量。
     *
     * @param source 原本的命令來源，只於 main dispatcher 呼叫。
     * @param key 安全的本地化原因。
     * @param cached 同 session 最後成功的快照。
     */
    private fun storageFailure(source: ServerCommandSource, key: String, cached: HistoryStorageSnapshot?) {
        if (cached == null) {
            source.sendError(prefixedConfigMessage(Text.translatable(key), Formatting.RED))
        } else {
            source.sendFeedback({ historyStorageMessage(cached, stale = true).append("\n  ").append(Text.translatable(key).formatted(Formatting.YELLOW)) }, false)
        }
    }

    private companion object {
        /** 歷史管理指令所需的 Minecraft 管理員等級。 */
        const val REQUIRED_PERMISSION_LEVEL: Int = 2

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** 歷史指令名稱。 */
        const val HISTORY_SUBCOMMAND: String = "history"

        /** 歷史狀態子指令名稱。 */
        const val STATUS_SUBCOMMAND: String = "status"

        /** 歷史重連子指令名稱。 */
        const val RETRY_SUBCOMMAND: String = "retry"

        /** 歷史用量子指令名稱。 */
        const val STORAGE_SUBCOMMAND: String = "storage"

        /** 清理子指令名稱。 */
        const val CLEANUP_SUBCOMMAND: String = "cleanup"

        /** 清理預覽子指令名稱。 */
        const val PREVIEW_SUBCOMMAND: String = "preview"

        /** 清理執行子指令名稱。 */
        const val RUN_SUBCOMMAND: String = "run"
    }
}
