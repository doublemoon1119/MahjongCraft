package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryScenario
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.StressDebugKeys
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import kotlinx.coroutines.launch
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Formatting
import kotlin.uuid.toKotlinUuid

/**
 * 建立 `/mahjongcraft debug stress` 的壓力測試指令：固定桌數、爬坡、狀態、停止與刪除資料庫。
 *
 * 開始測試的格式為 `/mahjongcraft debug stress fixed <情境> <桌數>` 與 `/mahjongcraft debug stress ramp <情境>`，後面可接選用的具名選項。
 * 選項以「名稱 值」成對出現，順序不限、只寫需要的（見 [StressOption]），例如 `fixed mahjongcraft:riichi_east 40 history off warmup 30`：
 * `pace realtime|fast`、`history write|encode|off`、`max_mspt <毫秒>`（判定卡頓的單次 tick 實際間隔，卡頓只記錄開始時的桌數，不會停止測試）、
 * `warmup <秒>`（0 表示不暖機）。沒有寫的選項使用 [StressRunOptions.DEFAULT]。
 *
 * 量測伺服器能承受的桌數時應使用專用伺服器：單人世界的內建伺服器與客戶端共用同一個程序與 CPU，客戶端的繪製與
 * 記憶體回收會拉高並擾動每 tick 耗時，打開遊戲選單時內建伺服器暫停也會中斷量測。在內建伺服器上開始時會提醒數據僅供參考。
 *
 * @property controller 執行壓力測試。
 * @property scope 執行 suspend 工作的應用作用域。
 * @property dispatchers 切換到伺服器主執行緒。
 */
class FabricDebugStressCommand(
    private val controller: StressTestController,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) {
    /** 建立可掛入 debug root 的壓力測試指令節點。 */
    fun build(): LiteralArgumentBuilder<ServerCommandSource> = literal(STRESS_SUBCOMMAND)
        .then(
            literal(FIXED_SUBCOMMAND).then(
                scenarioArgument().then(
                    withOptions(argument(TABLES_ARGUMENT, IntegerArgumentType.integer(1, STRESS_MAX_TABLES))) { context ->
                        StressTestMode.Fixed(IntegerArgumentType.getInteger(context, TABLES_ARGUMENT))
                    },
                ),
            ),
        )
        .then(literal(RAMP_SUBCOMMAND).then(withOptions(scenarioArgument()) { StressTestMode.Ramp(StressRampPlan()) }))
        .then(literal(STATUS_SUBCOMMAND).executes(::status))
        .then(literal(STOP_SUBCOMMAND).executes(::stop))
        .then(literal(CLEAR_SUBCOMMAND).executes(::clear))

    /** 情境引數，提供所有情境的 tab 補全。 */
    private fun scenarioArgument() = argument(SCENARIO_ARGUMENT, IdentifierArgumentType.identifier())
        .suggests { _, builder -> CommandSource.suggestMatching(HeadlessHistoryScenario.entries.map { it.identifier }, builder) }

    /**
     * 讓 [node] 本身可以執行，並接上選用的具名選項文字。
     *
     * @param T 接上選項後仍保留的指令節點型別。
     * @param node 可直接執行，或接上選用引數的指令節點。
     * @param mode 由指令內容取得桌數安排。
     * @return 接好執行器與選項引數的原節點。
     */
    private fun <T : ArgumentBuilder<ServerCommandSource, T>> withOptions(
        node: T,
        mode: (CommandContext<ServerCommandSource>) -> StressTestMode,
    ): T = node
        .executes { start(it, mode(it), optionsText = "") }
        .then(
            argument(OPTIONS_ARGUMENT, StringArgumentType.greedyString())
                .suggests { _, builder ->
                    val (offset, candidates) = stressOptionSuggestions(builder.remaining)
                    CommandSource.suggestMatching(candidates, builder.createOffset(builder.start + offset))
                }
                .executes { start(it, mode(it), StringArgumentType.getString(it, OPTIONS_ARGUMENT)) },
        )

    /**
     * 驗證選項並排程啟動工作；啟動結果由工作稍後回覆。
     *
     * @param context 指令來源與情境引數。
     * @param mode 固定桌數或逐級增加桌數的安排。
     * @param optionsText 尚未解析的具名選項文字。
     * @return 選項有誤時為失敗值，否則為排程成功值，不代表測試已啟動。
     */
    private fun start(
        context: CommandContext<ServerCommandSource>,
        mode: StressTestMode,
        optionsText: String,
    ): Int {
        val source = context.source
        val options = when (val parsed = parseStressRunOptions(optionsText)) {
            is StressOptionsParseResult.Parsed -> parsed.options
            is StressOptionsParseResult.Invalid -> {
                source.sendError(stressOptionErrorMessage(parsed.error))
                return FAILURE
            }
        }
        val scenarioId = IdentifierArgumentType.getIdentifier(context, SCENARIO_ARGUMENT).toString()
        scope.launch(dispatchers.main) {
            val result = controller.start(source.server, scenarioId, mode, options, source.player?.uuid?.toKotlinUuid())
            when (result) {
                StressTestStartResult.Started -> {
                    source.sendFeedback({ stressTestMessage(StressDebugKeys.STARTED, Formatting.GREEN) }, false)
                    if (!source.server.isDedicated) source.sendFeedback({ stressTestMessage(StressDebugKeys.INTEGRATED_SERVER_HINT, Formatting.YELLOW) }, false)
                }
                StressTestStartResult.Busy -> source.sendError(stressTestMessage(StressDebugKeys.BUSY, Formatting.RED))
                StressTestStartResult.InvalidScenario -> source.sendError(stressTestMessage(StressDebugKeys.INVALID_SCENARIO, Formatting.RED))
                StressTestStartResult.StorageUnavailable -> source.sendError(stressTestMessage(StressDebugKeys.STORAGE_UNAVAILABLE, Formatting.RED))
            }
        }
        return SUCCESS
    }

    /**
     * 回覆目前或最近一次的報告。
     *
     * @param context 接收報告或錯誤訊息的指令來源。
     * @return 有報告可輸出時為成功值，否則為失敗值。
     */
    private fun status(context: CommandContext<ServerCommandSource>): Int {
        val report = controller.report()
        if (report == null) {
            context.source.sendError(stressTestMessage(StressDebugKeys.NO_REPORT, Formatting.RED))
            return FAILURE
        }
        context.source.sendFeedback({ stressTestReportMessage(report) }, false)
        return SUCCESS
    }

    /**
     * 排程停止壓力測試，工作完成後回覆最終報告或未執行提示。
     *
     * @param context 接收結果的指令來源。
     * @return 工作排程成功值，不代表原本有測試正在執行。
     */
    private fun stop(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        scope.launch(dispatchers.main) {
            if (controller.stop()) {
                controller.report()?.let { report -> source.sendFeedback({ stressTestReportMessage(report) }, false) }
            } else {
                source.sendError(stressTestMessage(StressDebugKeys.NOT_RUNNING, Formatting.RED))
            }
        }
        return SUCCESS
    }

    /**
     * 排程清除壓力測試資料庫，工作完成後回覆清除結果。
     *
     * @param context 接收結果的指令來源。
     * @return 工作排程成功值，不代表資料庫已清除。
     */
    private fun clear(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        scope.launch(dispatchers.main) {
            when (controller.clear()) {
                StressTestClearResult.CLEARED -> source.sendFeedback({ stressTestMessage(StressDebugKeys.CLEARED, Formatting.GREEN) }, false)
                StressTestClearResult.NOTHING_TO_CLEAR -> source.sendError(stressTestMessage(StressDebugKeys.NOTHING_TO_CLEAR, Formatting.RED))
                StressTestClearResult.RUNNING -> source.sendError(stressTestMessage(StressDebugKeys.CLEAR_WHILE_RUNNING, Formatting.RED))
            }
        }
        return SUCCESS
    }

    private companion object {
        /** 壓力測試指令群組名稱。 */
        const val STRESS_SUBCOMMAND = "stress"

        /** 固定桌數測試的子指令名稱。 */
        const val FIXED_SUBCOMMAND = "fixed"

        /** 逐級增加桌數測試的子指令名稱。 */
        const val RAMP_SUBCOMMAND = "ramp"

        /** 查詢報告的子指令名稱。 */
        const val STATUS_SUBCOMMAND = "status"

        /** 停止測試的子指令名稱。 */
        const val STOP_SUBCOMMAND = "stop"

        /** 清除測試資料庫的子指令名稱。 */
        const val CLEAR_SUBCOMMAND = "clear"

        /** 測試情境 ID 的引數名稱。 */
        const val SCENARIO_ARGUMENT = "scenario"

        /** 固定桌數的引數名稱。 */
        const val TABLES_ARGUMENT = "tables"

        /** 具名選項文字的引數名稱。 */
        const val OPTIONS_ARGUMENT = "options"

        /** 指令已成功處理或排程的回傳值。 */
        const val SUCCESS = 1

        /** 指令未通過驗證或無結果可回覆的回傳值。 */
        const val FAILURE = 0
    }
}
