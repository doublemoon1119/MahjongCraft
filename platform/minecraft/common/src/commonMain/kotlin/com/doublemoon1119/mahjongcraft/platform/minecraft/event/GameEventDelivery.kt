package com.doublemoon1119.mahjongcraft.platform.minecraft.event

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchStartedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.GameEventContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MahjongCraftEvent
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MahjongCraftEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MatchEndedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MatchStartedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.RoundSettledListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.TableLocation
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
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
) {
    /** 以不可變快照原子更新的 session、佇列與交付水位。 */
    private val state = AtomicReference(DeliveryState(sessionId = null, releasedSequence = Long.MIN_VALUE))

    /** 開始新的伺服器 session，鎖定使用中的訂閱點並捨棄舊佇列；訂閱鎖定不隨 session 結束解除。 */
    fun startSession(): Uuid {
        matchStarted.lockRegistration()
        roundSettled.lockRegistration()
        matchEnded.lockRegistration()
        val sessionId = Uuid.random()
        state.store(DeliveryState(sessionId = sessionId, releasedSequence = Long.MIN_VALUE))
        return sessionId
    }

    /**
     * 結束指定 session；較舊 session 的排程工作不會再交付事件。
     * @param sessionId 欲結束的 session。
     */
    fun stopSession(sessionId: Uuid) {
        state.updateIfCurrent(sessionId) { DeliveryState(sessionId = null, releasedSequence = Long.MIN_VALUE) }
    }

    /**
     * 接收一筆已提交交易，僅投影並加入佇列，不直接呼叫第三方監聽者。
     * @param sessionId 接收者建立時擷取的 session。
     * @param sequence store 在交易內配置的遞增提交序號。
     * @param facts 同一交易中單一場地的事實。
     */
    fun onCommitted(
        sessionId: Uuid,
        sequence: Long,
        facts: CommittedGameFacts,
    ) {
        val current = state.load()
        if (current.sessionId != sessionId) return
        val events = projector.project(facts)
        if (events.isEmpty()) return
        val context = contextFor(events.first(), sessionId)
        val pending = events.map { event ->
            PendingEvent(
                sequence = sequence,
                event = event,
                context = context,
            )
        }
        while (true) {
            val loaded = state.load()
            if (loaded.sessionId != sessionId) return
            val next = loaded.copy(events = loaded.events + pending)
            if (state.compareAndSet(loaded, next)) break
        }
    }

    /**
     * 標示指定交易已釋放權威交易鎖，並安排所有已可交付事件。
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
     * @param context 第三方收到的情境。
     * @param sessionId 橋接目前持有的 session。
     * @return 情境與目前 session 均相符時為 true。
     */
    fun belongsToSession(context: GameEventContext, sessionId: Uuid): Boolean = context.sessionId == sessionId && state.load().sessionId == sessionId

    /**
     * 在提交時擷取位置，不延遲到交付時查詢。
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
                report { reporter.schedulingFailed(cause) }
            }
            return
        }
    }

    /**
     * 在主執行緒交付目前已解鎖的事件。
     * @param sessionId 此工作所屬 session。
     */
    private fun deliver(sessionId: Uuid) {
        while (true) {
            val ready = takeReady(sessionId)
            if (ready == null) return
            ready.forEach(::deliverOne)
        }
    }

    /**
     * 原子移出已解鎖事件，或在沒有可交付事件時解除排程旗標。
     * @param sessionId 此工作所屬 session。
     * @return 可交付事件；session 失效或沒有可交付事件時為 null。
     */
    private fun takeReady(sessionId: Uuid): List<PendingEvent>? {
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
     * @param pending 當次待交付事件。
     */
    private fun deliverOne(pending: PendingEvent) {
        when (val event = pending.event) {
            is MatchStartedEvent -> notify(pending, matchStarted.listenersSnapshot()) { it.onMatchStarted(event, pending.context) }
            is RoundSettledEvent -> notify(pending, roundSettled.listenersSnapshot()) { it.onRoundSettled(event, pending.context) }
            is MatchEndedEvent -> notify(pending, matchEnded.listenersSnapshot()) { it.onMatchEnded(event, pending.context) }
        }
    }

    /**
     * 逐一隔離監聽者失敗；生命週期切換後不再交付舊事件。
     * @param L 監聽者型別。
     * @param pending 當次事件。
     * @param listeners 訂閱者快照。
     * @param invoke 單一監聽者呼叫。
     */
    private fun <L> notify(
        pending: PendingEvent,
        listeners: List<L>,
        invoke: (L) -> Unit,
    ) {
        for (listener in listeners) {
            if (state.load().sessionId != pending.context.sessionId) return
            runCatching { invoke(listener) }.onFailure { cause ->
                report { reporter.listenerFailed(pending.event, cause) }
            }
        }
    }

    /**
     * 隔離回報器本身的失敗。
     * @param block 欲執行的回報。
     */
    private inline fun report(block: () -> Unit) {
        runCatching(block)
    }

    /**
     * 提交時擷取的單一通知。
     * @property sequence 來源交易序號。
     * @property event 公開事件。
     * @property context 提交當下的位置與 session。
     */
    private data class PendingEvent(
        val sequence: Long,
        val event: MatchEvent,
        val context: GameEventContext,
    )

    /**
     * 不可變的 session 交付狀態。
     * @property sessionId 有效 session；未啟動時為 null。
     * @property releasedSequence 已解鎖的最大序號。
     * @property events 依提交順序排列的待交付事件。
     * @property deliveryActive 是否已有排程或執行中的交付工作。
     */
    private data class DeliveryState(
        val sessionId: Uuid?,
        val releasedSequence: Long,
        val events: List<PendingEvent> = emptyList(),
        val deliveryActive: Boolean = false,
    ) {
        /** 是否至少有一筆事件已通過解鎖水位。 */
        fun hasReadyEvents(): Boolean = events.any { it.sequence <= releasedSequence }
    }

    /**
     * 僅更新仍屬於同一 session 的狀態。
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
