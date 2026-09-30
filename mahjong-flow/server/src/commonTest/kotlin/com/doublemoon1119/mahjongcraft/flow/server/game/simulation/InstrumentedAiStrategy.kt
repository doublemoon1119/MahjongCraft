package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.RoundPreparationAiContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * 一次 AI 決策的紀錄。
 *
 * @property strategyKey 做出決策的策略。
 * @property playerId 做出決策的玩家。
 * @property phase 決策情境。
 * @property legalActions 決策當下的合法動作。
 * @property command 送出的命令。
 * @property duration 決策花費的時間。
 */
internal data class DecisionRecord(
    val strategyKey: String,
    val playerId: Uuid,
    val phase: AiDecisionPhase,
    val legalActions: List<GameAction>,
    val command: GameCommand,
    val duration: Duration,
)

/**
 * 一個策略累計的決策次數與時間。
 *
 * @property count 決策次數。
 * @property total 總時間。
 * @property max 單次最長時間。
 */
internal data class DecisionTiming(
    val count: Int = 0,
    val total: Duration = Duration.ZERO,
    val max: Duration = Duration.ZERO,
) {
    /** 單次平均時間。 */
    val average: Duration get() = if (count == 0) Duration.ZERO else total / count

    /** 加入一次決策後的累計值。 */
    operator fun plus(duration: Duration): DecisionTiming = DecisionTiming(
        count = count + 1,
        total = total + duration,
        max = maxOf(max, duration),
    )

    /** 合併兩份累計值。 */
    operator fun plus(other: DecisionTiming): DecisionTiming = DecisionTiming(
        count = count + other.count,
        total = total + other.total,
        max = maxOf(max, other.max),
    )
}

/**
 * 累計每個策略的決策時間，並保留最近的決策作為失敗時的追蹤紀錄。
 *
 * @property traceLimit 保留的最近決策數。
 */
internal class DecisionRecorder(private val traceLimit: Int = DEFAULT_TRACE_LIMIT) {
    /** 每個策略的決策時間。 */
    private val timingByStrategy = mutableMapOf<String, DecisionTiming>()

    /** 最近的決策，舊的在前。 */
    private val recentDecisions = ArrayDeque<DecisionRecord>()

    /** 每個策略目前為止的決策時間。 */
    val timings: Map<String, DecisionTiming> get() = timingByStrategy.toMap()

    /** 最近的決策，舊的在前。 */
    val recent: List<DecisionRecord> get() = recentDecisions.toList()

    /** 記錄一次決策。 */
    fun record(decision: DecisionRecord) {
        timingByStrategy[decision.strategyKey] = timingByStrategy.getOrElse(decision.strategyKey) { DecisionTiming() } + decision.duration
        recentDecisions.addLast(decision)
        if (recentDecisions.size > traceLimit) recentDecisions.removeFirst()
    }

    /** [DecisionRecorder] 的預設值。 */
    private companion object {
        /** 預設保留的最近決策數。 */
        const val DEFAULT_TRACE_LIMIT = 40
    }
}

/**
 * AI 送出不在合法範圍內的命令。
 *
 * @property decision 違規的決策。
 */
internal class IllegalAiCommandException(val decision: DecisionRecord, reason: String) : IllegalStateException("${decision.strategyKey} sent an illegal command ${decision.command} (${decision.phase}): $reason")

/**
 * 包裝一個策略：記錄每次決策的時間與內容，並在命令不合法時拋出 [IllegalAiCommandException]。
 *
 * 合法範圍：回應捨牌或槓時只能送出合法動作或過；自己回合只能自摸、合法的槓與途中流局、擴充 handler 產生的命令，
 * 或打出自己手中的牌（規則強制捨牌時只能打出那張）。
 *
 * @property strategyKey 被包裝策略的登記 key。
 * @property delegate 被包裝的策略。
 * @property extensionActionRegistry 產生擴充動作命令候選的 registry。
 * @property recorder 決策紀錄。
 */
internal class InstrumentedAiStrategy(
    private val strategyKey: String,
    private val delegate: MahjongAiStrategy,
    private val extensionActionRegistry: ExtensionGameActionAiRegistry,
    private val recorder: DecisionRecorder,
) : MahjongAiStrategy {
    override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand {
        val mark = TimeSource.Monotonic.markNow()
        val command = delegate.decideGameCommand(context)
        val decision = DecisionRecord(
            strategyKey = strategyKey,
            playerId = context.selfId,
            phase = context.phase,
            legalActions = context.legalActions,
            command = command,
            duration = mark.elapsedNow(),
        )
        recorder.record(decision)
        illegalReason(context, command)?.let { throw IllegalAiCommandException(decision, it) }
        return command
    }

    override suspend fun decideRoundPreparation(context: RoundPreparationAiContext): RoundPreparationSubmission = delegate.decideRoundPreparation(context)

    /** [command] 不合法的原因；合法時為 null。 */
    private fun illegalReason(context: AiDecisionContext, command: GameCommand): String? = when (context.phase) {
        AiDecisionPhase.RespondingToDiscard -> when (command) {
            is GameCommand.RespondToDiscard -> responseReason(context, command.action)
            else -> "expected RespondToDiscard"
        }

        AiDecisionPhase.RespondingToKan -> when (command) {
            is GameCommand.RespondToKan -> responseReason(context, command.action)
            else -> "expected RespondToKan"
        }

        AiDecisionPhase.OwnTurn -> checkOwnTurn(context, command)
    }

    /** 回應時的動作必須是過或合法動作之一。 */
    private fun responseReason(context: AiDecisionContext, action: GameAction): String? = if (action == GameAction.Pass || action in context.legalActions) null else "action $action is not legal"

    /** 自己回合的命令檢查。 */
    private fun checkOwnTurn(context: AiDecisionContext, command: GameCommand): String? {
        val legal = context.legalActions
        return when (command) {
            GameCommand.Tsumo -> if (GameAction.Tsumo in legal) null else "tsumo is not legal"
            is GameCommand.Kan -> if (legal.any { it is GameAction.Kan && it.type == command.type && it.tileId == command.tileId }) null else "kan is not legal"
            is GameCommand.DeclareExhaustiveDraw -> if (GameAction.ExhaustiveDraw(command.reason) in legal) null else "abortive draw is not legal"
            is GameCommand.Discard -> {
                val ownTileIds = context.snapshot.players.first { it.id == context.selfId }.hand.standingTiles.map { it.id }
                when {
                    command.tileId !in ownTileIds -> "tile ${command.tileId} is not in hand"
                    context.forcedDiscardTileId != null && command.tileId != context.forcedDiscardTileId -> "must discard ${context.forcedDiscardTileId}"
                    else -> null
                }
            }
            is GameCommand.Extension -> {
                val candidates = legal.filterIsInstance<GameAction.Extension>()
                    .flatMap { extensionActionRegistry.createCandidates(it.value, context) }
                    .map { it.command }
                if (command in candidates) null else "extension command is not a legal candidate"
            }
            else -> "unexpected command on own turn"
        }
    }
}

/**
 * 把 [source] 中 [keys] 的策略包裝成 [InstrumentedAiStrategy] 後登記到這個 registry。
 *
 * @param source 提供原始策略的 registry。
 * @param keys 要包裝的策略 key。
 * @param extensionActionRegistry 產生擴充動作命令候選的 registry。
 * @param recorder 決策紀錄。
 */
internal fun MahjongAiStrategyRegistry.registerInstrumented(
    source: MahjongAiStrategyRegistry,
    keys: Collection<String>,
    extensionActionRegistry: ExtensionGameActionAiRegistry,
    recorder: DecisionRecorder,
) {
    keys.forEach { key ->
        register(key) {
            InstrumentedAiStrategy(
                strategyKey = key,
                delegate = source.resolve(key),
                extensionActionRegistry = extensionActionRegistry,
                recorder = recorder,
            )
        }
    }
}
