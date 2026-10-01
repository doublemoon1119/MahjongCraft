package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryScenario
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.fabric.text.historyGenerationProgressMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.text.prefixedConfigMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.HistoryDebugKeys
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.koin.core.annotation.Single
import java.util.concurrent.CompletableFuture

/** 建立 `/mahjongcraft debug history` 的歷史生成、狀態及取消指令。
 *
 * @property controller 管理單一 session 的生成批次。
 * @property writer 驗證非同步回覆仍屬於原存檔 session。
 * @property scope 執行 suspend controller 工作的應用作用域。
 * @property dispatchers 將結果回覆切回伺服器主執行緒。
 */
@Single
class FabricDebugHistoryCommand(
    private val controller: HistoryGenerationController,
    private val writer: FabricHistoryOutboxWriter,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) {
    /** 建立可掛入既有 debug root 的歷史指令節點。 */
    fun build(): LiteralArgumentBuilder<ServerCommandSource> = literal(HISTORY_SUBCOMMAND)
        .then(
            literal(GENERATE_SUBCOMMAND)
                .then(
                    argument(SCENARIO_ARGUMENT, IdentifierArgumentType.identifier())
                        .suggests(::suggestScenarios)
                        .then(argument(COUNT_ARGUMENT, IntegerArgumentType.integer(MIN_COUNT, MAX_COUNT)).executes(::generate)),
                ),
        )
        .then(literal(STATUS_SUBCOMMAND).executes { status(it) })
        .then(literal(CANCEL_SUBCOMMAND).executes { cancel(it) })

    /** 開始一批有界歷史生成工作。
     * @param context Brigadier 指令內容。
     * @return 指令已接受的結果碼；實際工作於協程中執行。
     */
    private fun generate(context: CommandContext<ServerCommandSource>): Int {
        val scenario = IdentifierArgumentType.getIdentifier(context, SCENARIO_ARGUMENT).toString()
        val count = IntegerArgumentType.getInteger(context, COUNT_ARGUMENT)
        val source = context.source
        val session = writer.currentSessionId
        if (session == null) {
            source.sendError(message(HistoryDebugKeys.STORAGE_UNAVAILABLE, "History storage is unavailable", Formatting.RED))
            return FAILURE
        }
        scope.launch(dispatchers.main) {
            val result = controller.start(scenario, count, session)
            withContext(dispatchers.main) {
                if (!writer.isCurrentSession(session)) return@withContext
                when (result) {
                    HistoryGenerationStartResult.Started -> source.sendFeedback({ message(HistoryDebugKeys.STARTED, "History generation started", Formatting.GREEN) }, false)
                    HistoryGenerationStartResult.Busy -> source.sendError(message(HistoryDebugKeys.BUSY, "A history generation batch is already running", Formatting.RED))
                    HistoryGenerationStartResult.InvalidScenario -> source.sendError(message(HistoryDebugKeys.INVALID_SCENARIO, "Invalid history generation scenario", Formatting.RED))
                    HistoryGenerationStartResult.SessionUnavailable -> source.sendError(message(HistoryDebugKeys.STORAGE_UNAVAILABLE, "History storage is unavailable", Formatting.RED))
                    HistoryGenerationStartResult.PolicyRejected -> source.sendError(message(HistoryDebugKeys.POLICY_REJECTED, "History generation was rejected by policy", Formatting.RED))
                    HistoryGenerationStartResult.StorageUnavailable -> source.sendError(message(HistoryDebugKeys.STORAGE_UNAVAILABLE, "History storage is unavailable", Formatting.RED))
                }
            }
        }
        return SUCCESS
    }

    /** 回報目前批次的完整安全統計。
     * @param context Brigadier 指令內容。
     * @return 指令結果碼。
     */
    private fun status(context: CommandContext<ServerCommandSource>): Int {
        val progress = controller.snapshot()
        if (progress == null) {
            context.source.sendError(message(HistoryDebugKeys.NO_BATCH, "There is no active history generation batch", Formatting.RED))
        } else {
            context.source.sendFeedback({ historyGenerationProgressMessage(progress) }, false)
        }
        return if (progress == null) FAILURE else SUCCESS
    }

    /** 要求目前場次完成後停止批次。
     * @param context Brigadier 指令內容。
     * @return 指令結果碼。
     */
    private fun cancel(context: CommandContext<ServerCommandSource>): Int {
        if (!controller.cancel()) {
            context.source.sendError(message(HistoryDebugKeys.NO_BATCH, "There is no active history generation batch", Formatting.RED))
            return FAILURE
        }
        context.source.sendFeedback({ message(HistoryDebugKeys.CANCEL_REQUESTED, "History generation cancellation requested", Formatting.YELLOW) }, false)
        return SUCCESS
    }

    /** 提供東風與半莊情境的 tab 補全。
     * @param context Brigadier 指令內容。
     * @param builder 補全結果建構器。
     * @return 非同步補全結果。
     */
    @Suppress("UNUSED_PARAMETER")
    private fun suggestScenarios(context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> = CommandSource.suggestMatching(HeadlessHistoryScenario.entries.map { it.identifier }, builder)

    /** 建立帶有固定前綴與英文 fallback 的安全回覆。
     * @param key 翻譯鍵。
     * @param fallback 英文 fallback。
     * @param color 回覆顏色。
     * @return 可送出的訊息。
     */
    private fun message(key: String, fallback: String, color: Formatting) = prefixedConfigMessage(Text.translatableWithFallback(key, fallback), color)

    private companion object {
        /** 歷史生成指令群組名稱。 */
        const val HISTORY_SUBCOMMAND = "history"

        /** 批次生成子指令名稱。 */
        const val GENERATE_SUBCOMMAND = "generate"

        /** 進度查詢子指令名稱。 */
        const val STATUS_SUBCOMMAND = "status"

        /** 取消子指令名稱。 */
        const val CANCEL_SUBCOMMAND = "cancel"

        /** 情境引數名稱。 */
        const val SCENARIO_ARGUMENT = "scenario"

        /** 場數引數名稱。 */
        const val COUNT_ARGUMENT = "count"

        /** 最少生成場數。 */
        const val MIN_COUNT = 1

        /** 最多生成場數。 */
        const val MAX_COUNT = 100

        /** Brigadier 成功回傳值。 */
        const val SUCCESS = 1

        /** Brigadier 失敗回傳值。 */
        const val FAILURE = 0
    }
}
