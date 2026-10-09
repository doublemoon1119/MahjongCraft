package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.RoundPreparationAiContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.state.HistoryRecordingObserver
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * 累計無頭對局推進中各環節的耗時，供壓力測試拆解單步耗時。
 *
 * 量測的環節：建立 AI 視角快照（[aiContext]）、策略思考（[aiDecision]，可能在背景執行緒上）、快照同步（為每位觀察者裁切可見快照）、
 * 歷史記錄（權威交易中比對桌況並加入待寫佇列；作為權威來源的觀察者時才有）。呼叫端另外以 [addAiWait] 記下等待 AI 結果的時間，
 * 單步總耗時扣掉它就是主執行緒上的耗時。另外計算過期而沒有套用的 AI 決策數（[staleDecisions]）。
 *
 * 另外記下本區間最久的一次 AI 出牌決策，以及正在進行中的 AI 出牌決策，用來找出異常緩慢的決策情境。
 *
 * 呼叫端在每個量測區間開始前 [reset]，結束後讀取各項累計值；同一時間只量測一個區間，同一個區間內的寫入依序發生（策略思考
 * 可能在其他執行緒上，但結束後才回到呼叫端）。只有 [ongoingAiDecision] 可以在區間進行中從其他執行緒讀取。
 */
class HeadlessStepTimer : HistoryRecordingObserver {
    /** 本區間策略思考的累計耗時。 */
    var aiDecision: Duration = Duration.ZERO
        private set

    /** 本區間建立 AI 視角快照的累計耗時。 */
    var aiContext: Duration = Duration.ZERO
        private set

    /** 本區間等待 AI 決策結果的累計時間，含排隊與回到呼叫端。 */
    var aiWait: Duration = Duration.ZERO
        private set

    /** 本區間因權威遊戲已改變而沒有套用的 AI 決策數。 */
    var staleDecisions: Int = 0
        private set

    /** 本區間快照同步的累計耗時。 */
    var snapshotSync: Duration = Duration.ZERO
        private set

    /** 本區間歷史記錄的累計耗時。 */
    var historyRecording: Duration = Duration.ZERO
        private set

    /** 本區間新加入待寫佇列的歷史事件數。 */
    var historyEvents: Int = 0
        private set

    /** 本區間最久的一次 AI 出牌決策；本區間沒有出牌決策時為 null。 */
    var slowestAiDecision: TimedAiDecision? = null
        private set

    /** 正在進行中的 AI 出牌決策；沒有時為 null。可從其他執行緒讀取，用來發現遲遲沒有結束的決策。 */
    @Volatile var ongoingAiDecision: OngoingAiDecision? = null
        private set

    /** 歸零所有累計值，開始新的量測區間。 */
    fun reset() {
        aiDecision = Duration.ZERO
        aiContext = Duration.ZERO
        aiWait = Duration.ZERO
        staleDecisions = 0
        snapshotSync = Duration.ZERO
        historyRecording = Duration.ZERO
        historyEvents = 0
        slowestAiDecision = null
    }

    /** 一次 AI 出牌決策開始。 */
    internal fun beginAiDecision(context: AiDecisionContext) {
        ongoingAiDecision = OngoingAiDecision(context, TimeSource.Monotonic.markNow())
    }

    /** 進行中的 AI 出牌決策結束，耗時 [duration]。 */
    internal fun endAiDecision(context: AiDecisionContext, duration: Duration) {
        ongoingAiDecision = null
        if (duration > (slowestAiDecision?.duration ?: Duration.ZERO)) slowestAiDecision = TimedAiDecision(context, duration)
    }

    /** 累計一次策略思考耗時。 */
    internal fun addAiDecision(duration: Duration) {
        aiDecision += duration
    }

    /** 累計一次建立 AI 視角快照的耗時。 */
    internal fun addAiContext(duration: Duration) {
        aiContext += duration
    }

    /**
     * 累計一次等待 AI 決策結果的時間。
     *
     * @param duration 從開始決策到得到結果的時間。
     */
    fun addAiWait(duration: Duration) {
        aiWait += duration
    }

    /** 記下一次因權威遊戲已改變而沒有套用的 AI 決策。 */
    internal fun addStaleDecision() {
        staleDecisions++
    }

    /** 累計一次快照同步耗時。 */
    internal fun addSnapshotSync(duration: Duration) {
        snapshotSync += duration
    }

    override fun onHistoryRecorded(duration: Duration, appendedEvents: Int) {
        historyRecording += duration
        historyEvents += appendedEvents
    }
}

/**
 * 一次已結束的 AI 出牌決策。
 *
 * @property context 決策情境。
 * @property duration 策略思考的耗時。
 */
data class TimedAiDecision(val context: AiDecisionContext, val duration: Duration)

/**
 * 一次進行中的 AI 出牌決策。
 *
 * @property context 決策情境。
 * @property startedAt 開始的時刻。
 */
class OngoingAiDecision(val context: AiDecisionContext, val startedAt: TimeMark)

/**
 * 量測每次產生快照耗時的觀看政策。
 *
 * @property delegate 實際產生快照的政策。
 * @property record 接收每次產生快照的耗時。
 */
internal class TimedVisibilityPolicy(
    private val delegate: GameVisibilityPolicy,
    private val record: (Duration) -> Unit,
) : GameVisibilityPolicy {
    override fun snapshotFor(game: Game, observerId: Uuid): TableStateSnapshot = measured { delegate.snapshotFor(game, observerId) }

    override fun roundPreparationSnapshotFor(game: Game, observerId: Uuid): RoundPreparationSnapshot? = measured { delegate.roundPreparationSnapshotFor(game, observerId) }

    /** 執行 [block] 並記錄耗時。 */
    private inline fun <T> measured(block: () -> T): T {
        val mark = TimeSource.Monotonic.markNow()
        return block().also { record(mark.elapsedNow()) }
    }
}

/**
 * 解析出的策略都會量測決策耗時的策略登記中心。
 *
 * @property delegate 實際的策略登記中心。
 * @property timer 接收每次決策的耗時與情境。
 */
internal class TimedAiStrategyRegistry(
    private val delegate: MahjongAiStrategyRegistry,
    private val timer: HeadlessStepTimer,
) : MahjongAiStrategyRegistry by delegate {
    override fun resolve(key: String?): MahjongAiStrategy = TimedAiStrategy(delegate.resolve(key), timer)
}

/**
 * 量測每次決策耗時的策略。
 *
 * @property delegate 實際的策略。
 * @property timer 接收每次決策的耗時與情境。
 */
private class TimedAiStrategy(
    private val delegate: MahjongAiStrategy,
    private val timer: HeadlessStepTimer,
) : MahjongAiStrategy {
    override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand {
        timer.beginAiDecision(context)
        val mark = TimeSource.Monotonic.markNow()
        try {
            return delegate.decideGameCommand(context)
        } finally {
            val duration = mark.elapsedNow()
            timer.endAiDecision(context, duration)
            timer.addAiDecision(duration)
        }
    }

    override suspend fun decideRoundPreparation(context: RoundPreparationAiContext): RoundPreparationSubmission {
        val mark = TimeSource.Monotonic.markNow()
        return delegate.decideRoundPreparation(context).also { timer.addAiDecision(mark.elapsedNow()) }
    }
}
