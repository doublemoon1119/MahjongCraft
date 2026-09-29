package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.registerBuiltInAiStrategies
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_STICK_POINTS
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 一位玩家在一局中的結果。
 *
 * @property playerId 玩家。
 * @property strategyKey 玩家使用的策略。
 * @property scoreDelta 這一局的分數變化，包含立直棒與本場。
 * @property won 是否以自摸或榮和和牌。
 * @property dealtIn 是否放銃。
 */
internal data class SeatRoundResult(
    val playerId: Uuid,
    val strategyKey: String,
    val scoreDelta: Int,
    val won: Boolean,
    val dealtIn: Boolean,
)

/**
 * 模擬中偵測到的問題。
 *
 * @property reason 問題的說明。
 * @property round 發生問題的一局；無法判斷時為 null。
 * @property cause 造成問題的例外；沒有例外時為 null。
 * @property trace 發生問題前最近的 AI 決策。
 * @property reproducedOnReplay 從 [round] 的開局桌況重新推進時是否再次發生問題；沒有可重播的局時為 null。
 */
internal data class SimulationFailure(
    val reason: String,
    val round: RecordedRound?,
    val cause: Throwable?,
    val trace: List<DecisionRecord>,
    val reproducedOnReplay: Boolean?,
) {
    /** 供測試失敗訊息使用的完整描述。 */
    fun describe(): String = buildString {
        appendLine("Simulation failure: $reason")
        cause?.let { appendLine("Cause: $it") }
        round?.let {
            appendLine("Round: ${it.start.roundPosition}, combo ${it.start.comboCount}, reproduced on replay: $reproducedOnReplay")
            appendLine(
                "Last state: live wall ${it.final.tileWall.getAllTiles().size} tiles, reserved ${it.final.reservedWallTiles.size} tiles, " +
                    "dynamic state ${it.final.dynamicRuleState}",
            )
        }
        appendLine("Recent decisions (oldest first):")
        trace.forEach { appendLine("  ${it.strategyKey} ${it.playerId} ${it.phase}: ${it.command} (legal: ${it.legalActions})") }
    }
}

/**
 * 一場模擬對局的結果。
 *
 * @property strategyKeysByPlayer 每位玩家使用的策略。
 * @property rounds 每一局每位玩家的結果，依開局順序排列。
 * @property placementsByPlayer 整場結束時每位玩家的名次，第一名為 1；對局未正常結束時為空。
 * @property failure 偵測到的問題；正常結束時為 null。
 */
internal data class MatchResult(
    val strategyKeysByPlayer: Map<Uuid, String>,
    val rounds: List<List<SeatRoundResult>>,
    val placementsByPlayer: Map<Uuid, Int>,
    val failure: SimulationFailure?,
)

/**
 * 以正式規則與流程讓 AI 打完一整場對局，並檢查：沒有送出不合法命令、流程沒有卡住、點數守恆、對局正常結束。
 *
 * 每一局開局時的權威桌況都會記錄下來；偵測到問題時，從發生問題那一局的開局桌況重新推進一次，確認問題能重現。
 * 內建期望值策略是確定性的，同一個開局桌況一定得到同樣的過程；隨機出牌策略則不保證重現。
 * 點數守恆以日麻計算：所有玩家的分數加上場上立直棒的點數，等於開局總分。
 *
 * @property config 對局規則設定。
 * @property registerStrategies 在原始策略 registry 登記可用的策略；預設為所有內建策略。
 */
internal class MatchSimulator(
    private val config: MahjongRuleConfig,
    private val registerStrategies: (registry: MahjongAiStrategyRegistry, runtime: SimulationRuntime) -> Unit = { registry, runtime ->
        registry.registerBuiltInAiStrategies(
            moduleRegistry = runtime.moduleRegistry,
            extensionActionRegistry = runtime.extensionActionRegistry,
            opponentModelRegistry = runtime.opponentModelRegistry,
        )
    },
) {
    /**
     * 依 [lineup] 的策略讓每位玩家打完一整場；座位由開局流程隨機決定。
     *
     * @param lineup 每位玩家使用的策略 key。
     * @param recorder 累計決策時間與最近決策的紀錄。
     */
    suspend fun play(lineup: List<String>, recorder: DecisionRecorder = DecisionRecorder()): MatchResult {
        val runtime = runtime(recorder)
        val gameId = Uuid.random()
        val playerIds = lineup.map { Uuid.random() }
        val strategyKeysByPlayer = playerIds.zip(lineup).toMap()
        val initial = GameInitializer.initialize(
            id = gameId,
            playerIds = playerIds,
            module = runtime.moduleRegistry.getModule(config),
            aiPlayerStrategyKeys = strategyKeysByPlayer,
        ).tableState
        runtime.gameRepository.setTableState(initial)

        val problem = drive(runtime, gameId, recorder)
        val recordedRounds = runtime.gameRepository.rounds(gameId)
        val failure = problem?.let { (reason, cause) ->
            val failingRound = recordedRounds.firstOrNull { !isConserved(it.start) || !isConserved(it.final) } ?: recordedRounds.lastOrNull()
            SimulationFailure(
                reason = reason,
                round = failingRound,
                cause = cause,
                trace = recorder.recent,
                reproducedOnReplay = failingRound?.let { replayFails(it.start) },
            )
        }
        val finalState = recordedRounds.last().final
        return MatchResult(
            strategyKeysByPlayer = strategyKeysByPlayer,
            rounds = recordedRounds.map { roundResults(it, strategyKeysByPlayer) },
            placementsByPlayer = if (failure == null) placements(runtime, finalState) else emptyMap(),
            failure = failure,
        )
    }

    /** 從 [start] 重新推進一次，回傳是否再次發生問題。 */
    private suspend fun replayFails(start: TableState): Boolean {
        val recorder = DecisionRecorder()
        val runtime = runtime(recorder)
        runtime.gameRepository.setTableState(start)
        return drive(runtime, start.id, recorder) != null
    }

    /**
     * 推進對局直到停止，回傳問題的說明與例外；正常結束時為 null。
     *
     * 流程停在整場結束之前時，把最後一次 AI 決策的命令重新送出一次，附上流程拒絕它的原因。
     */
    private suspend fun drive(
        runtime: SimulationRuntime,
        gameId: Uuid,
        recorder: DecisionRecorder,
    ): Pair<String, Throwable?>? {
        val cause = runCatching { runtime.coordinator.driveAutomatedPlayers(gameId) }.exceptionOrNull()
        if (cause != null) return "flow stopped with an exception" to cause
        val rounds = runtime.gameRepository.rounds(gameId)
        rounds.firstOrNull { !isConserved(it.start) || !isConserved(it.final) }?.let {
            return "points are not conserved in round ${it.start.roundPosition}" to null
        }
        if (runtime.gameRepository.getGame(gameId)?.pendingTransition != PendingGameTransition.ReturnToRoom) {
            val rejection = recorder.recent.lastOrNull()?.let { last ->
                (runtime.coordinator(gameId, last.playerId, last.command) as? Outcome.Error)?.error
            }
            return "the match stopped before it ended; last command rejected with: $rejection" to null
        }
        return null
    }

    /** 所有玩家的分數加上場上立直棒的點數是否等於開局總分。 */
    private fun isConserved(state: TableState): Boolean {
        val sticks = (state.dynamicRuleState as? RiichiDynamicState)?.riichiStickCount ?: 0
        return state.players.sumOf { it.score } + sticks * RIICHI_STICK_POINTS == state.players.size * config.scoreConfig.initialScore
    }

    /** 一局中每位玩家的分數變化、和牌與放銃。 */
    private fun roundResults(round: RecordedRound, strategyKeysByPlayer: Map<Uuid, String>): List<SeatRoundResult> {
        val final = round.final
        val winnerIds = final.players
            .filter { player -> player.actionHistory.any { it is GameAction.Ron || it == GameAction.Tsumo } }
            .mapTo(mutableSetOf()) { it.id }
        val ronTileIds = final.players.flatMap { player -> player.actionHistory.filterIsInstance<GameAction.Ron>().map { it.tileId } }.toSet()
        val dealtInIds = final.players
            .filter { player ->
                player.id !in winnerIds &&
                    (player.discardPile.entries.any { it.tile.id in ronTileIds } || player.hand.allTiles.any { it.id in ronTileIds })
            }
            .mapTo(mutableSetOf()) { it.id }
        return final.players.map { player ->
            SeatRoundResult(
                playerId = player.id,
                strategyKey = strategyKeysByPlayer.getValue(player.id),
                scoreDelta = player.score - round.start.players.first { it.id == player.id }.score,
                won = player.id in winnerIds,
                dealtIn = player.id in dealtInIds,
            )
        }
    }

    /** 依規則的整場排名決定名次。 */
    private fun placements(runtime: SimulationRuntime, finalState: TableState): Map<Uuid, Int> = finalState.players
        .sortedWith(runtime.moduleRegistry.getModule(config).compareForMatchRanking())
        .mapIndexed { index, player -> player.id to index + 1 }
        .toMap()

    /** 建立一個新的對局環境，AI 策略包裝成記錄決策並檢查合法性的版本。 */
    private fun runtime(recorder: DecisionRecorder): SimulationRuntime = SimulationRuntime(defaultStrategyKey = BuiltInAiStrategyKeys.BEGINNER).also { runtime ->
        val source = MahjongAiStrategyRegistryImpl(defaultKey = BuiltInAiStrategyKeys.BEGINNER)
        registerStrategies(source, runtime)
        runtime.aiStrategyRegistry.registerInstrumented(
            source = source,
            keys = source.getAllStrategyKeys(),
            extensionActionRegistry = runtime.extensionActionRegistry,
            recorder = recorder,
        )
    }
}
