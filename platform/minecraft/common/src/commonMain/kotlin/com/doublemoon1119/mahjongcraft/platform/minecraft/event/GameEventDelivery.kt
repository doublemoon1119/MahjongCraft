package com.doublemoon1119.mahjongcraft.platform.minecraft.event

import com.doublemoon1119.mahjongcraft.flow.api.event.ActionsPerformedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchStartedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundCompletedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RuleResultsResolvedEvent
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.ActionsPerformedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.EventBatchCommittedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.GameEventContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MahjongCraftEvent
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MahjongCraftEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MatchEndedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MatchStartedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.RoundCompletedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.RoundSettledListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.RuleResultsResolvedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.TableLocation
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * 將已提交的規則中立事實投影為 Minecraft 平台事件，並在交易釋放後依序交付。
 *
 * 事件投影只建立平台事件資料，不直接呼叫第三方；所有監聽者都經由排入平台佇列的工作呼叫。
 * [startSession] 與 [stopSession] 用來捨棄伺服器切換時尚未交付的舊事件。
 * 生命週期操作與交付在同一主執行緒執行；提交通知由 store 在鎖內依序呼叫，解鎖通知允許亂序。
 * 排程失敗時保留佇列，後續通知再嘗試排程；session 結束時捨棄尚未交付的事件，不補發。
 *
 * @property projector 將提交事實投影為公開事件的元件。
 * @property scheduler 保證將交付工作排入平台主執行緒佇列的元件。
 * @property locations 取得事件產生當下桌子位置的來源。
 * @property reporter 事件交付診斷回報器。
 * @property matchStarted 對局開始訂閱點。
 * @property roundSettled 一局結算訂閱點。
 * @property matchEnded 對局結束訂閱點。
 * @property failurePolicy 判斷監聽者、排程器或回報器丟出的失敗能否隔離；無法隔離的失敗原樣往外傳。
 * @property actionsPerformed 已成立動作訂閱點。
 * @property roundCompleted 本局完成訂閱點。
 * @property eventBatchCommitted 交易事件批次訂閱點。
 * @property ruleResultsResolved 已成立規則結果訂閱點。
 */
@OptIn(ExperimentalAtomicApi::class)
class GameEventDelivery(
    private val projector: GameEventProjector,
    private val scheduler: GameEventScheduler,
    private val locations: GameEventLocationSource,
    private val reporter: GameEventDeliveryReporter,
    private val matchStarted: MahjongCraftEvent<MatchStartedListener> = MahjongCraftEvents.MATCH_STARTED,
    private val roundSettled: MahjongCraftEvent<RoundSettledListener> = MahjongCraftEvents.ROUND_SETTLED,
    private val matchEnded: MahjongCraftEvent<MatchEndedListener> = MahjongCraftEvents.MATCH_ENDED,
    private val failurePolicy: ExtensionFailurePolicy,
    private val actionsPerformed: MahjongCraftEvent<ActionsPerformedListener> = MahjongCraftEvents.ACTIONS_PERFORMED,
    private val roundCompleted: MahjongCraftEvent<RoundCompletedListener> = MahjongCraftEvents.ROUND_COMPLETED,
    private val eventBatchCommitted: MahjongCraftEvent<EventBatchCommittedListener> = MahjongCraftEvents.EVENT_BATCH_COMMITTED,
    private val ruleResultsResolved: MahjongCraftEvent<RuleResultsResolvedListener> = MahjongCraftEvents.RULE_RESULTS_RESOLVED,
) {
    /** 以不可變快照原子更新的 session、佇列與交付水位。 */
    private val state = AtomicReference(DeliveryState(sessionId = null, releasedSequence = Long.MIN_VALUE))

    /**
     * 開始新的伺服器 session，鎖定使用中的訂閱點並捨棄舊佇列；訂閱鎖定不隨 session 結束解除。
     *
     * @return 新 session 的 UUID，供交易通知與事件情境驗證使用。
     */
    fun startSession(): Uuid {
        matchStarted.lockRegistration()
        roundSettled.lockRegistration()
        matchEnded.lockRegistration()
        actionsPerformed.lockRegistration()
        roundCompleted.lockRegistration()
        eventBatchCommitted.lockRegistration()
        ruleResultsResolved.lockRegistration()
        val sessionId = Uuid.random()
        state.store(DeliveryState(sessionId = sessionId, releasedSequence = Long.MIN_VALUE))
        return sessionId
    }

    /**
     * 結束指定 session；較舊 session 的排程工作不會再交付事件。
     *
     * @param sessionId 欲結束的 session。
     */
    fun stopSession(sessionId: Uuid) {
        state.updateIfCurrent(sessionId) { DeliveryState(sessionId = null, releasedSequence = Long.MIN_VALUE) }
    }

    /**
     * 接收一筆已提交交易，僅投影並加入佇列，不直接呼叫第三方監聽者。
     *
     * @param sessionId 接收者建立時擷取的 session。
     * @param sequence store 在交易內配置的遞增提交序號。
     * @param facts 同一交易中單一場地的事實。
     * @param onBatchDelivered 解鎖後接收同一公開批次的內建處理；可持有服務與 session 檢查，不保留對局或事實。
     */
    fun onCommitted(
        sessionId: Uuid,
        sequence: Long,
        facts: CommittedGameFacts,
        onBatchDelivered: ((CommittedMatchEventBatch) -> Unit)? = null,
    ) {
        val current = state.load()
        if (current.sessionId != sessionId) return
        val projection = projector.projectBatchResult(facts)
        val batch = projection.batch ?: return
        val context = contextFor(batch.events.first(), sessionId)
        val pending = PendingBatch(
            sequence = sequence,
            batch = batch,
            context = context,
            sessionId = sessionId,
            onBatchDelivered = onBatchDelivered.takeIf { !projection.markersFailed },
        )
        while (true) {
            val loaded = state.load()
            if (loaded.sessionId != sessionId) return
            val next = loaded.copy(events = loaded.events + pending)
            if (state.compareAndSet(loaded, next)) break
        }
    }

    /**
     * 標示指定交易已釋放權威交易鎖，並安排所有已可交付事件。
     *
     * @param sessionId 通知所屬 session。
     * @param sequence 已釋放交易鎖的提交序號；亂序通知不會使水位倒退。
     */
    fun onReleased(sessionId: Uuid, sequence: Long) {
        while (true) {
            val current = state.load()
            if (current.sessionId != sessionId) return
            val next = current.copy(releasedSequence = maxOf(current.releasedSequence, sequence))
            if (state.compareAndSet(current, next)) break
        }
        scheduleIfReady(sessionId)
    }

    /**
     * 判斷事件情境是否仍屬於指定的有效 session。
     *
     * @param context 第三方收到的情境。
     * @param sessionId 橋接目前持有的 session。
     * @return 情境與目前 session 均相符時為 true。
     */
    fun belongsToSession(context: GameEventContext, sessionId: Uuid): Boolean = context.sessionId == sessionId && state.load().sessionId == sessionId

    /**
     * 在提交時擷取位置，不延遲到交付時查詢。
     *
     * @param event 本次投影事件。
     * @param sessionId 事件所屬 session。
     * @return 保存位置與 session 的唯讀情境。
     */
    private fun contextFor(event: MatchEvent, sessionId: Uuid): GameEventContext {
        val location = locations.find(event.venueId)
        if (location == null) report { reporter.missingLocation(event.matchId, event.venueId) }
        val publicLocation = location?.let {
            TableLocation.create(
                dimensionId = it.dimensionId,
                x = it.x,
                y = it.y,
                z = it.z,
            )
        }
        return GameEventContext.create(publicLocation, sessionId)
    }

    /**
     * 只有一個交付工作可同時排程或執行。
     *
     * @param sessionId 欲排程的 session。
     */
    private fun scheduleIfReady(sessionId: Uuid) {
        while (true) {
            val current = state.load()
            if (current.sessionId != sessionId || !current.hasReadyEvents()) return
            if (current.deliveryActive) return
            val next = current.copy(deliveryActive = true)
            if (!state.compareAndSet(current, next)) continue
            try {
                scheduler.enqueue { deliver(sessionId) }
            } catch (cause: Throwable) {
                state.updateIfCurrent(sessionId) { it.copy(deliveryActive = false) }
                if (!failurePolicy.isIsolatable(cause)) throw cause
                report { reporter.schedulingFailed(cause) }
            }
            return
        }
    }

    /**
     * 在主執行緒交付目前已解鎖的事件。
     *
     * @param sessionId 此工作所屬 session。
     */
    private fun deliver(sessionId: Uuid) {
        try {
            while (true) {
                val ready = takeReady(sessionId)
                if (ready == null) return
                ready.forEach(::deliverBatch)
            }
        } catch (cause: Throwable) {
            // 無法隔離的失敗中斷這次交付；解除排程旗標，避免之後的事件永遠不再排程。
            state.updateIfCurrent(sessionId) { it.copy(deliveryActive = false) }
            throw cause
        }
    }

    /**
     * 原子移出已解鎖事件，或在沒有可交付事件時解除排程旗標。
     *
     * @param sessionId 此工作所屬 session。
     * @return 可交付事件；session 失效或沒有可交付事件時為 null。
     */
    private fun takeReady(sessionId: Uuid): List<PendingBatch>? {
        while (true) {
            val current = state.load()
            if (current.sessionId != sessionId) return null
            val ready = current.events.takeWhile { it.sequence <= current.releasedSequence }
            if (ready.isNotEmpty()) {
                val next = current.copy(events = current.events.drop(ready.size))
                if (state.compareAndSet(current, next)) return ready
                continue
            }
            val next = current.copy(deliveryActive = false)
            if (!state.compareAndSet(current, next)) continue
            scheduleIfReady(sessionId)
            return null
        }
    }

    /**
     * 依事件類型取得訂閱者快照。
     *
     * @param pending 當次待交付事件。
     */
    private fun deliverBatch(pending: PendingBatch) {
        val batch = pending.batch
        batch.events.forEach { event -> deliverOne(pending, event) }
        if (state.load().sessionId != pending.sessionId) return
        // 內建處理與公開監聽者使用相同的失敗隔離，出錯時不影響監聽者與後續批次。
        pending.onBatchDelivered?.let { handler -> notify(pending = pending, listeners = listOf(handler), event = null) { it(batch) } }
        val context = checkNotNull(pending.context)
        notify(
            pending = pending,
            listeners = eventBatchCommitted.listenersSnapshot(),
            event = null,
        ) {
            it.onEventBatchCommitted(batch, context)
        }
    }

    /** 交付批次內的單一事件，各類型仍使用各自的訂閱點。 */
    private fun deliverOne(pending: PendingBatch, event: MatchEvent) {
        val context = checkNotNull(pending.context)
        when (event) {
            is MatchStartedEvent -> notify(
                pending = pending,
                listeners = matchStarted.listenersSnapshot(),
                event = event,
            ) { it.onMatchStarted(event, context) }
            is RoundSettledEvent -> notify(
                pending = pending,
                listeners = roundSettled.listenersSnapshot(),
                event = event,
            ) { it.onRoundSettled(event, context) }
            is MatchEndedEvent -> notify(
                pending = pending,
                listeners = matchEnded.listenersSnapshot(),
                event = event,
            ) { it.onMatchEnded(event, context) }
            is ActionsPerformedEvent -> notify(
                pending = pending,
                listeners = actionsPerformed.listenersSnapshot(),
                event = event,
            ) { it.onActionsPerformed(event, context) }
            is RoundCompletedEvent -> notify(
                pending = pending,
                listeners = roundCompleted.listenersSnapshot(),
                event = event,
            ) { it.onRoundCompleted(event, context) }
            is RuleResultsResolvedEvent -> notify(
                pending = pending,
                listeners = ruleResultsResolved.listenersSnapshot(),
                event = event,
            ) { it.onRuleResultsResolved(event, context) }
        }
    }

    /**
     * 逐一隔離監聽者失敗；生命週期切換後不再交付舊事件。
     *
     * 交付不在協程中執行，監聽者丟出的取消例外不代表交付被取消，與可以隔離的失敗一樣只略過該監聽者。
     *
     * @param L 監聽者型別。
     * @param pending 當次事件。
     * @param listeners 訂閱者快照。
     * @param event 正在交付的個別事件；批次監聽者為 null，改用批次診斷。
     * @param invoke 單一監聽者呼叫。
     */
    private fun <L : Any> notify(
        pending: PendingBatch,
        listeners: List<L>,
        event: MatchEvent?,
        invoke: (L) -> Unit,
    ) {
        for (listener in listeners) {
            if (state.load().sessionId != pending.sessionId) return
            try {
                invoke(listener)
            } catch (cause: Throwable) {
                if (cause !is CancellationException && !failurePolicy.isIsolatable(cause)) throw cause
                report {
                    if (event == null) {
                        reporter.batchListenerFailed(checkNotNull(pending.batch), listener, cause)
                    } else {
                        reporter.listenerFailed(event, listener, cause)
                    }
                }
            }
        }
    }

    /**
     * 隔離回報器本身可以隔離的失敗。
     *
     * @param block 欲執行的回報。
     */
    private inline fun report(block: () -> Unit) {
        try {
            block()
        } catch (cause: Throwable) {
            if (!failurePolicy.isIsolatable(cause)) throw cause
        }
    }

    /**
     * 提交時擷取的事件批次。
     *
     * @property sequence 來源交易序號。
     * @property batch 同一筆提交產生的公開事件批次。
     * @property context 公開事件提交當下的位置與 session。
     * @property sessionId 此筆提交所屬 session。
     * @property onBatchDelivered 與此筆提交一起交付的內建批次接收端，不持有原始對局或事實。
     */
    private data class PendingBatch(
        val sequence: Long,
        val batch: CommittedMatchEventBatch,
        val context: GameEventContext,
        val sessionId: Uuid,
        val onBatchDelivered: ((CommittedMatchEventBatch) -> Unit)?,
    )

    /**
     * 不可變的 session 交付狀態。
     *
     * @property sessionId 有效 session；未啟動時為 null。
     * @property releasedSequence 已解鎖的最大序號。
     * @property events 依提交順序排列的待交付事件。
     * @property deliveryActive 是否已有排程或執行中的交付工作。
     */
    private data class DeliveryState(
        val sessionId: Uuid?,
        val releasedSequence: Long,
        val events: List<PendingBatch> = emptyList(),
        val deliveryActive: Boolean = false,
    ) {
        /** 是否至少有一筆事件已通過解鎖水位。 */
        fun hasReadyEvents(): Boolean = events.any { it.sequence <= releasedSequence }
    }

    /**
     * 僅更新仍屬於同一 session 的狀態。
     *
     * @param sessionId 欲更新的 session。
     * @param transform 根據目前快照產生新狀態。
     */
    private inline fun AtomicReference<DeliveryState>.updateIfCurrent(
        sessionId: Uuid,
        transform: (DeliveryState) -> DeliveryState,
    ) {
        while (true) {
            val current = load()
            if (current.sessionId != sessionId) return
            if (compareAndSet(current, transform(current))) return
        }
    }
}
