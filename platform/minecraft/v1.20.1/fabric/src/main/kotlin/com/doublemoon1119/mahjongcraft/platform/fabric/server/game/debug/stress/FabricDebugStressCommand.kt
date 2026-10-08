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
import org.koin.core.annotation.Single
import kotlin.uuid.toKotlinUuid

/**
 * 建立 `/mahjongcraft debug stress` 的壓力測試指令：固定桌數、爬坡、狀態、停止與刪除資料庫。
 *
 * 開始測試的格式為 `/mahjongcraft debug stress fixed <情境> <桌數> [realtime|fast] [write|encode|off] [max_mspt]` 與
 * `/mahjongcraft debug stress ramp <情境> [realtime|fast] [write|encode|off] [max_mspt]`；省略時為真實節奏、寫入資料庫。
 * `max_mspt` 是判定卡頓的單一 tick 耗時毫秒數，預設 100；卡頓只記錄開始時的桌數，不會停止測試。
 *
 * 量測伺服器能承受的桌數時應使用專用伺服器：單人世界的內建伺服器與客戶端共用同一個程序與 CPU，客戶端的繪製與
 * 記憶體回收會拉高並擾動每 tick 耗時，打開遊戲選單時內建伺服器暫停也會中斷量測。在內建伺服器上開始時會提醒數據僅供參考。
 *
 * @property controller 執行壓力測試。
 * @property scope 執行 suspend 工作的應用作用域。
 * @property dispatchers 切換到伺服器主執行緒。
 */
@Single
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
                    withOptionalSettings(argument(TABLES_ARGUMENT, IntegerArgumentType.integer(1, STRESS_MAX_TABLES))) { context ->
                        StressTestMode.Fixed(IntegerArgumentType.getInteger(context, TABLES_ARGUMENT))
                    },
                ),
            ),
        )
        .then(literal(RAMP_SUBCOMMAND).then(withOptionalSettings(scenarioArgument()) { StressTestMode.Ramp(StressRampPlan()) }))
        .then(literal(STATUS_SUBCOMMAND).executes(::status))
        .then(literal(STOP_SUBCOMMAND).executes(::stop))
        .then(literal(CLEAR_SUBCOMMAND).executes(::clear))

    /** 情境引數，提供所有情境的 tab 補全。 */
    private fun scenarioArgument() = argument(SCENARIO_ARGUMENT, IdentifierArgumentType.identifier())
        .suggests { _, builder -> CommandSource.suggestMatching(HeadlessHistoryScenario.entries.map { it.identifier }, builder) }

    /**
     * 讓 [node] 本身可以執行，並依序接上選用的節奏、歷史處理方式與每 tick 耗時上限引數。
     *
     * @param mode 由指令內容取得桌數安排。
     */
    private fun <T : ArgumentBuilder<ServerCommandSource, T>> withOptionalSettings(
        node: T,
        mode: (CommandContext<ServerCommandSource>) -> StressTestMode,
    ): T = node
        .executes { start(it, mode(it), StressTestPace.REALTIME, StressHistoryMode.WRITE, StressSafetyThresholds()) }
        .then(
            argument(PACE_ARGUMENT, StringArgumentType.word())
                .suggests { _, builder -> CommandSource.suggestMatching(StressTestPace.entries.map { it.commandName }, builder) }
                .executes { start(it, mode(it), pace(it) ?: return@executes FAILURE, StressHistoryMode.WRITE, StressSafetyThresholds()) }
                .then(
                    argument(HISTORY_MODE_ARGUMENT, StringArgumentType.word())
                        .suggests { _, builder -> CommandSource.suggestMatching(StressHistoryMode.entries.map { it.commandName }, builder) }
                        .executes {
                            start(it, mode(it), pace(it) ?: return@executes FAILURE, historyMode(it) ?: return@executes FAILURE, StressSafetyThresholds())
                        }
                        .then(
                            argument(MAX_MSPT_ARGUMENT, IntegerArgumentType.integer(1)).executes {
                                val thresholds = StressSafetyThresholds(stutterLimitMillis = IntegerArgumentType.getInteger(it, MAX_MSPT_ARGUMENT).toDouble())
                                start(it, mode(it), pace(it) ?: return@executes FAILURE, historyMode(it) ?: return@executes FAILURE, thresholds)
                            },
                        ),
                ),
        )

    /** 讀取節奏引數；名稱不存在時回覆錯誤並回傳 null。 */
    private fun pace(context: CommandContext<ServerCommandSource>): StressTestPace? {
        val name = StringArgumentType.getString(context, PACE_ARGUMENT)
        return StressTestPace.entries.firstOrNull { it.commandName == name }.also { pace ->
            if (pace == null) context.source.sendError(stressTestMessage(StressDebugKeys.INVALID_PACE, Formatting.RED))
        }
    }

    /** 讀取歷史處理方式引數；名稱不存在時回覆錯誤並回傳 null。 */
    private fun historyMode(context: CommandContext<ServerCommandSource>): StressHistoryMode? {
        val name = StringArgumentType.getString(context, HISTORY_MODE_ARGUMENT)
        return StressHistoryMode.entries.firstOrNull { it.commandName == name }.also { historyMode ->
            if (historyMode == null) context.source.sendError(stressTestMessage(StressDebugKeys.INVALID_HISTORY_MODE, Formatting.RED))
        }
    }

    /** 開始壓力測試並回覆結果。 */
    private fun start(
        context: CommandContext<ServerCommandSource>,
        mode: StressTestMode,
        pace: StressTestPace,
        historyMode: StressHistoryMode,
        thresholds: StressSafetyThresholds,
    ): Int {
        val source = context.source
        val scenarioId = IdentifierArgumentType.getIdentifier(context, SCENARIO_ARGUMENT).toString()
        scope.launch(dispatchers.main) {
            val result = controller.start(source.server, scenarioId, mode, pace, historyMode, thresholds, source.player?.uuid?.toKotlinUuid())
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

    /** 回覆目前或最近一次的報告。 */
    private fun status(context: CommandContext<ServerCommandSource>): Int {
        val report = controller.report()
        if (report == null) {
            context.source.sendError(stressTestMessage(StressDebugKeys.NO_REPORT, Formatting.RED))
            return FAILURE
        }
        context.source.sendFeedback({ stressTestReportMessage(report) }, false)
        return SUCCESS
    }

    /** 停止壓力測試並回覆最終報告。 */
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

    /** 刪除壓力測試資料庫並回覆結果。 */
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
        const val STRESS_SUBCOMMAND = "stress"
        const val FIXED_SUBCOMMAND = "fixed"
        const val RAMP_SUBCOMMAND = "ramp"
        const val STATUS_SUBCOMMAND = "status"
        const val STOP_SUBCOMMAND = "stop"
        const val CLEAR_SUBCOMMAND = "clear"
        const val SCENARIO_ARGUMENT = "scenario"
        const val TABLES_ARGUMENT = "tables"
        const val PACE_ARGUMENT = "pace"
        const val HISTORY_MODE_ARGUMENT = "history"
        const val MAX_MSPT_ARGUMENT = "max_mspt"
        const val SUCCESS = 1
        const val FAILURE = 0
    }
}
