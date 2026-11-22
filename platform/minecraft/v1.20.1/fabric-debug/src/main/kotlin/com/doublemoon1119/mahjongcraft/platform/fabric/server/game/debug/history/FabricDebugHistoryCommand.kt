package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryScenario
import com.doublemoon1119.mahjongcraft.platform.fabric.text.historyGenerationProgressMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.HistoryDebugKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.HistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text.prefixedConfigMessage
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

/**
 * 建立 `/mahjongcraft debug history` 的歷史生成、狀態及取消指令。
 *
 * @property controller 管理單一 session 的生成批次。
 * @property writer 驗證非同步回覆仍屬於原存檔 session。
 * @property scope 執行 suspend controller 工作的應用作用域。
 * @property dispatchers 將結果回覆切回伺服器主執行緒。
 */
class FabricDebugHistoryCommand(
    private val controller: HistoryGenerationController,
    private val writer: HistoryOutboxWriter,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) {
    /** 建立可掛入既有 debug root 的歷史指令節點。 */
    fun build(): LiteralArgumentBuilder<CommandSourceStack> = literal(HISTORY_SUBCOMMAND)
        .then(
            literal(GENERATE_SUBCOMMAND)
                .then(
                    argument(SCENARIO_ARGUMENT, ResourceLocationArgument.id())
                        .suggests(::suggestScenarios)
                        .then(argument(COUNT_ARGUMENT, IntegerArgumentType.integer(MIN_COUNT, MAX_COUNT)).executes(::generate)),
                ),
        )
        .then(literal(STATUS_SUBCOMMAND).executes { status(it) })
        .then(literal(CANCEL_SUBCOMMAND).executes { cancel(it) })

    /**
     * 開始一批有界歷史生成工作。
     *
     * @param context Brigadier 指令內容。
     * @return 指令已接受的結果碼；實際工作於協程中執行。
     */
    private fun generate(context: CommandContext<CommandSourceStack>): Int {
        val scenario = ResourceLocationArgument.getId(context, SCENARIO_ARGUMENT).toString()
        val count = IntegerArgumentType.getInteger(context, COUNT_ARGUMENT)
        val source = context.source
        val session = writer.currentSessionId
        if (session == null) {
            source.sendFailure(message(HistoryDebugKeys.STORAGE_UNAVAILABLE, "History storage is unavailable", ChatFormatting.RED))
            return FAILURE
        }
        scope.launch(dispatchers.main) {
            val result = controller.start(scenario, count, session)
            withContext(dispatchers.main) {
                if (!writer.isCurrentSession(session)) return@withContext
                when (result) {
                    HistoryGenerationStartResult.Started -> source.sendSuccess({ message(HistoryDebugKeys.STARTED, "History generation started", ChatFormatting.GREEN) }, false)
                    HistoryGenerationStartResult.Busy -> source.sendFailure(message(HistoryDebugKeys.BUSY, "A history generation batch is already running", ChatFormatting.RED))
                    HistoryGenerationStartResult.InvalidScenario -> source.sendFailure(message(HistoryDebugKeys.INVALID_SCENARIO, "Invalid history generation scenario", ChatFormatting.RED))
                    HistoryGenerationStartResult.SessionUnavailable -> source.sendFailure(message(HistoryDebugKeys.STORAGE_UNAVAILABLE, "History storage is unavailable", ChatFormatting.RED))
                    HistoryGenerationStartResult.PolicyRejected -> source.sendFailure(message(HistoryDebugKeys.POLICY_REJECTED, "History generation was rejected by policy", ChatFormatting.RED))
                    HistoryGenerationStartResult.StorageUnavailable -> source.sendFailure(message(HistoryDebugKeys.STORAGE_UNAVAILABLE, "History storage is unavailable", ChatFormatting.RED))
                }
            }
        }
        return SUCCESS
    }

    /**
     * 回報目前批次的完整安全統計。
     *
     * @param context Brigadier 指令內容。
     * @return 指令結果碼。
     */
    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val progress = controller.snapshot()
        if (progress == null) {
            context.source.sendFailure(message(HistoryDebugKeys.NO_BATCH, "There is no active history generation batch", ChatFormatting.RED))
        } else {
            context.source.sendSuccess({ historyGenerationProgressMessage(progress) }, false)
        }
        return if (progress == null) FAILURE else SUCCESS
    }

    /**
     * 要求目前場次完成後停止批次。
     *
     * @param context Brigadier 指令內容。
     * @return 指令結果碼。
     */
    private fun cancel(context: CommandContext<CommandSourceStack>): Int {
        if (!controller.cancel()) {
            context.source.sendFailure(message(HistoryDebugKeys.NO_BATCH, "There is no active history generation batch", ChatFormatting.RED))
            return FAILURE
        }
        context.source.sendSuccess({ message(HistoryDebugKeys.CANCEL_REQUESTED, "History generation cancellation requested", ChatFormatting.YELLOW) }, false)
        return SUCCESS
    }

    /**
     * 提供所有情境的 tab 補全。
     *
     * @param context Brigadier 指令內容。
     * @param builder 補全結果建構器。
     * @return 非同步補全結果。
     */
    private fun suggestScenarios(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> = SharedSuggestionProvider.suggest(HeadlessHistoryScenario.entries.map { it.identifier }, builder)

    /**
     * 建立帶有固定前綴與英文 fallback 的安全回覆。
     *
     * @param key 翻譯鍵。
     * @param fallback 英文 fallback。
     * @param color 回覆顏色。
     * @return 可送出的訊息。
     */
    private fun message(key: String, fallback: String, color: ChatFormatting) = prefixedConfigMessage(Component.translatableWithFallback(key, fallback), color)

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
