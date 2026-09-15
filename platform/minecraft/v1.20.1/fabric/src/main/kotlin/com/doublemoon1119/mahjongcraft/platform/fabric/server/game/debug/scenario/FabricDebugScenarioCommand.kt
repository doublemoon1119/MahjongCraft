package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
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
import org.koin.core.annotation.Single
import java.util.concurrent.CompletableFuture
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/** 建立 `/mahjongcraft debug scenario` 的 development-only 指令子樹。 */
@Single
class FabricDebugScenarioCommand(
    private val registry: DebugGameScenarioRegistry,
    private val loader: DebugGameScenarioLoader,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) {
    /** 建立可掛入既有 debug root 的 scenario 指令節點。 */
    fun build(): LiteralArgumentBuilder<ServerCommandSource> = literal(SCENARIO_SUBCOMMAND)
        .then(literal(LIST_SUBCOMMAND).executes(::listScenarios))
        .then(
            literal(LOAD_SUBCOMMAND).then(
                argument(SCENARIO_ID_ARGUMENT, IdentifierArgumentType.identifier())
                    .suggests(::suggestScenarioIds)
                    .executes(::loadScenario),
            ),
        )
        .then(
            literal(STRESS_SUBCOMMAND).then(
                argument(SCENARIO_ID_ARGUMENT, IdentifierArgumentType.identifier())
                    .suggests(::suggestScenarioIds)
                    .then(
                        argument(PRESENTATION_COUNT_ARGUMENT, IntegerArgumentType.integer(MIN_PRESENTATION_COUNT, MAX_PRESENTATION_COUNT))
                            .executes(::stressScenario),
                    ),
            ),
        )

    /** 將所有情境 ID 輸出至指令來源。 */
    private fun listScenarios(context: CommandContext<ServerCommandSource>): Int {
        val message = buildString {
            appendLine("Debug scenarios:")
            registry.getAll().forEach { scenario -> appendLine("- ${scenario.id}") }
        }.trimEnd()
        context.source.sendFeedback({ Text.literal(message) }, false)
        return SUCCESS
    }

    /** 非同步載入指定情境，完成後回到 server main thread 輸出結果。 */
    private fun loadScenario(context: CommandContext<ServerCommandSource>): Int = runScenarioCommand(context) { playerId, scenarioId -> loader.load(playerId, scenarioId) }

    /** 非同步重複載入指定情境的 presentation，供 entity 生成與開局時序壓力測試。 */
    private fun stressScenario(context: CommandContext<ServerCommandSource>): Int {
        val presentationCount = IntegerArgumentType.getInteger(context, PRESENTATION_COUNT_ARGUMENT)
        return runScenarioCommand(context) { playerId, scenarioId -> loader.stress(playerId, scenarioId, presentationCount) }
    }

    /** 共用 scenario 指令的玩家驗證、非同步執行與結果輸出。 */
    private fun runScenarioCommand(
        context: CommandContext<ServerCommandSource>,
        operation: suspend (Uuid, String) -> DebugGameScenarioLoadResult,
    ): Int {
        val source = context.source
        val player = runCatching { source.playerOrThrow }.getOrElse {
            source.sendError(Text.literal("This debug scenario requires a player"))
            return FAILURE
        }
        val scenarioId = IdentifierArgumentType.getIdentifier(context, SCENARIO_ID_ARGUMENT).toString()
        scope.launch {
            val result = operation(player.uuid.toKotlinUuid(), scenarioId)
            withContext(dispatchers.main) {
                when (result) {
                    is DebugGameScenarioLoadResult.Success -> source.sendFeedback(
                        {
                            val suffix = if (result.presentationCount > 1) {
                                " (${result.presentationCount} presentations)"
                            } else {
                                ""
                            }
                            Text.literal("Loaded debug scenario: ${result.scenarioId}$suffix")
                        },
                        false,
                    )

                    is DebugGameScenarioLoadResult.Rejected -> source.sendError(Text.literal(result.reason))
                }
            }
        }
        return SUCCESS
    }

    /** 依 registry 內容提供 scenario ID tab 補全。 */
    @Suppress("UNUSED_PARAMETER")
    private fun suggestScenarioIds(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = CommandSource.suggestMatching(registry.getAll().map { it.id }, builder)

    private companion object {
        /** 指令成功回傳值。 */
        const val SUCCESS: Int = 1

        /** 指令失敗回傳值。 */
        const val FAILURE: Int = 0

        /** Scenario 指令群組名稱。 */
        const val SCENARIO_SUBCOMMAND: String = "scenario"

        /** 列舉情境的子指令名稱。 */
        const val LIST_SUBCOMMAND: String = "list"

        /** 載入情境的子指令名稱。 */
        const val LOAD_SUBCOMMAND: String = "load"

        /** 重複重建情境 presentation 的子指令名稱。 */
        const val STRESS_SUBCOMMAND: String = "stress"

        /** 情境 ID 引數名稱。 */
        const val SCENARIO_ID_ARGUMENT: String = "scenario_id"

        /** Presentation 重建次數的引數名稱。 */
        const val PRESENTATION_COUNT_ARGUMENT: String = "presentation_count"

        /** 壓力測試至少執行的 presentation 次數。 */
        const val MIN_PRESENTATION_COUNT: Int = 2

        /** 單次指令允許的 presentation 重建上限。 */
        const val MAX_PRESENTATION_COUNT: Int = 100
    }
}
